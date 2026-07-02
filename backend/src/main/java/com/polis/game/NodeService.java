package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Contested resource buildings on resource islands — siege-style control points. A player OCCUPIES an
 * uncontrolled building (their troops garrison it); allies of the controller SUPPORT it (reinforce and
 * share the payout); enemies ATTACK to seize it (winner's troops become the new garrison, their
 * alliance takes control). While controlled it pays out every {@code polis.node-payout-ms} (10 min):
 * resources split by each garrisoning player's troop-population share, delivered to that player's city.
 * Control time accrues to the controlling alliance for the {@link AllianceTierService} tier gate.
 */
@Service
public class NodeService {
  public static final long CONTESTED_WINDOW_SECONDS = 30 * 60;

  private final ResourceNodeRepo nodes;
  private final CityRepo cities;
  private final PlayerRepo players;
  private final AllianceRepo alliances;
  private final IslandRepo islands;
  private final UnitCatalog catalog;
  private final TravelTimeService travel;
  private final BuildService build;
  private final HeroService heroService;
  private final HeroRepo heroRepo;
  private final MovementRepo movements;
  private final MissionService missions;
  private final CityService cityService;
  private final AllianceTierService tierProgress;

  public NodeService(ResourceNodeRepo nodes, CityRepo cities, PlayerRepo players, AllianceRepo alliances,
                     IslandRepo islands, UnitCatalog catalog, TravelTimeService travel, BuildService build,
                     HeroService heroService, HeroRepo heroRepo, MovementRepo movements, MissionService missions,
                     CityService cityService, AllianceTierService tierProgress){
    this.nodes=nodes; this.cities=cities; this.players=players; this.alliances=alliances; this.islands=islands;
    this.catalog=catalog; this.travel=travel; this.build=build; this.heroService=heroService; this.heroRepo=heroRepo;
    this.movements=movements; this.missions=missions; this.cityService=cityService; this.tierProgress=tierProgress;
  }

  // --- garrison helpers (per-player) ------------------------------------------

  private int stackPop(Map<String,Integer> stack){
    int p = 0; for (var e : stack.entrySet()) p += catalog.get(e.getKey()).getPopulationCost() * e.getValue(); return p;
  }
  private int totalPop(ResourceNode n){
    int p = 0; for (var s : n.getGarrison().values()) p += stackPop(s); return p;
  }
  private int playerPop(ResourceNode n, Long pid){
    Map<String,Integer> s = n.getGarrison().get(String.valueOf(pid));
    return s == null ? 0 : stackPop(s);
  }
  /** Flatten all players' stacks into one unit→qty map (for combat as the node's defenders). */
  private Map<String,Integer> flatGarrison(ResourceNode n){
    Map<String,Integer> flat = new LinkedHashMap<>();
    for (var s : n.getGarrison().values()) for (var e : s.entrySet()) flat.merge(e.getKey(), e.getValue(), Integer::sum);
    return flat;
  }
  private int tierOf(ResourceNode n){ return islands.findById(n.getIslandId()).map(Island::getTier).orElse(1); }

  // --- read endpoints ---------------------------------------------------------

  @Transactional
  public List<Map<String,Object>> islandNodes(Long islandId){
    List<Map<String,Object>> out = new ArrayList<>();
    for (ResourceNode n : nodes.findByIslandId(islandId)) out.add(dto(n, null));
    return out;
  }
  @Transactional
  public Map<String,Object> node(Long nodeId){
    ResourceNode n = nodes.findById(nodeId).orElseThrow(() -> new IllegalArgumentException("Node not found"));
    return dto(n, com.polis.config.SecurityConfig.currentPlayerId());
  }
  @Transactional
  public List<Map<String,Object>> myNodes(Long playerId){
    List<Map<String,Object>> out = new ArrayList<>();
    for (ResourceNode n : nodes.findByControllingPlayerId(playerId)) out.add(dto(n, playerId));
    return out;
  }

  public Map<String,Object> dto(ResourceNode n){ return dto(n, com.polis.config.SecurityConfig.currentPlayerId()); }

  public Map<String,Object> dto(ResourceNode n, Long viewerId){
    int cap = NodeRules.garrisonCap(n.getLevel());
    int pop = totalPop(n);
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", n.getId());
    m.put("islandId", n.getIslandId());
    m.put("islandName", islands.findById(n.getIslandId()).map(Island::getName).orElse("?"));
    m.put("tier", tierOf(n));
    m.put("x", n.getX()); m.put("y", n.getY());
    m.put("nodeType", n.getNodeType().name());
    m.put("producedResource", n.getNodeType().producedResource.name());
    m.put("level", n.getLevel());
    m.put("status", n.getStatus().name());
    m.put("controllingPlayerId", n.getControllingPlayerId());
    m.put("controllingAllianceId", n.getControllingAllianceId());
    m.put("controllingAllianceName", n.getControllingAllianceId()==null ? null :
        alliances.findById(n.getControllingAllianceId()).map(Alliance::getName).orElse(null));
    m.put("controllingAllianceEmblem", n.getControllingAllianceId()==null ? null :
        alliances.findById(n.getControllingAllianceId()).map(Alliance::getEmblem).orElse(null));
    m.put("garrisonPop", pop);
    m.put("garrisonCap", cap);
    m.put("ratePerHour", Math.round(NodeRules.ratePerHour(n.getLevel(), pop)));
    // Per-payout-cycle (10 min) generation for this single resource. `ratePer10Min` is what the
    // current garrison yields; `maxRatePer10Min` is the node's rated output at full garrison — shown
    // so an UNCLAIMED node still advertises what it can generate (instead of a bare 0).
    m.put("ratePer10Min", Math.round(NodeRules.ratePerHour(n.getLevel(), pop) / 6.0));
    m.put("maxRatePer10Min", Math.round(NodeRules.ratePerHour(n.getLevel(), cap) / 6.0));
    m.put("controlSince", n.getControlSince()==null ? null : n.getControlSince().toString());

    // per-player garrison + share
    List<Map<String,Object>> holders = new ArrayList<>();
    for (var e : n.getGarrison().entrySet()){
      Long pid = Long.valueOf(e.getKey());
      int ppop = stackPop(e.getValue());
      Map<String,Object> h = new LinkedHashMap<>();
      h.put("playerId", pid);
      h.put("playerName", players.findById(pid).map(Player::getUsername).orElse("Unknown"));
      h.put("troops", e.getValue());
      h.put("pop", ppop);
      h.put("sharePct", pop > 0 ? Math.round(ppop * 1000.0 / pop) / 10.0 : 0.0);
      holders.add(h);
    }
    m.put("holders", holders);
    m.put("myPop", viewerId == null ? 0 : playerPop(n, viewerId));
    m.put("mySharePct", (viewerId != null && pop > 0) ? Math.round(playerPop(n, viewerId) * 1000.0 / pop) / 10.0 : 0.0);
    // does the viewer's alliance currently control this building? (drives support-vs-attack in the UI)
    Long viewerAlliance = viewerId == null ? null : allianceOf(viewerId);
    m.put("viewerControls", n.getStatus() == NodeStatus.CONTROLLED && sameAlliance(n.getControllingAllianceId(), viewerAlliance));
    m.put("name", label(n));
    return m;
  }

  public String label(ResourceNode n){
    String t = switch (n.getNodeType()){
      case SACRED_GROVE -> "Sacred Grove"; case MARBLE_QUARRY -> "Marble Quarry"; case WHEAT_FIELD -> "Wheat Field";
    };
    return t + " — Lv " + n.getLevel();
  }

  // --- player actions ---------------------------------------------------------

  private Long allianceOf(Long playerId){ return players.findById(playerId).map(Player::getAllianceId).orElse(null); }
  private boolean sameAlliance(Long a, Long b){ return a != null && a.equals(b); }

  @Transactional
  public Movement occupy(Long playerId, Long nodeId, Long cityId, Map<String,Integer> troops, Long heroId){
    ResourceNode n = nodes.findById(nodeId).orElseThrow(() -> new IllegalArgumentException("Building not found"));
    if (n.getStatus() == NodeStatus.CONTROLLED)
      throw new IllegalStateException("This building is controlled — support it (if allied) or attack it");
    return dispatch(playerId, cityId, n, troops, MovementPhase.OCCUPY, heroId);
  }

  /** Ally reinforcement: only players in the controlling alliance (or the controller) may support. */
  @Transactional
  public Movement support(Long playerId, Long nodeId, Long cityId, Map<String,Integer> troops){
    ResourceNode n = nodes.findById(nodeId).orElseThrow(() -> new IllegalArgumentException("Building not found"));
    if (n.getStatus() != NodeStatus.CONTROLLED) throw new IllegalStateException("Nothing to support — occupy it instead");
    if (!sameAlliance(n.getControllingAllianceId(), allianceOf(playerId)))
      throw new IllegalStateException("Only the controlling alliance can support this building");
    return dispatch(playerId, cityId, n, troops, MovementPhase.OCCUPY, null);
  }

  /** Enemy attack: only players NOT in the controlling alliance may attack to seize. */
  @Transactional
  public Movement attack(Long playerId, Long nodeId, Long cityId, Map<String,Integer> troops, Long heroId){
    ResourceNode n = nodes.findById(nodeId).orElseThrow(() -> new IllegalArgumentException("Building not found"));
    if (n.getStatus() == NodeStatus.CONTROLLED && sameAlliance(n.getControllingAllianceId(), allianceOf(playerId)))
      throw new IllegalStateException("Your alliance controls this — support it instead of attacking");
    return dispatch(playerId, cityId, n, troops, MovementPhase.OUT, heroId);
  }

  private Movement dispatch(Long playerId, Long cityId, ResourceNode n, Map<String,Integer> troops,
                            MovementPhase phase, Long heroId){
    City src = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(src.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    if (troops == null || troops.isEmpty()) throw new IllegalArgumentException("Select at least one unit");
    Hero hero = heroId != null ? heroService.requireOwned(playerId, heroId) : null;
    int heroLoad = hero != null ? travel.heroLandLoad(hero.getRace()) : 0;
    travel.requireTransport(troops, src.getIslandId(), n.getIslandId(), heroLoad);
    build.deductGarrison(cityId, troops);
    boolean water = travel.crossesWater(src.getIslandId(), n.getIslandId());
    long secs = travel.seconds(src.getIslandId(), n.getIslandId(), travel.effectiveMinutesPerTile(troops, water));
    if (hero != null) secs = (long)(secs * heroService.travelMult(hero));
    Movement m = new Movement();
    m.setWorldId(src.getWorldId()); m.setPlayerId(playerId); m.setSourceCityId(cityId);
    m.setTargetNodeId(n.getId()); m.setPhase(phase);
    m.setUnits(new HashMap<>(troops)); m.setArriveAt(Instant.now().plusSeconds(Math.max(5, secs)));
    Movement saved = movements.save(m);
    if (heroId != null) heroService.sendHero(playerId, heroId, cityId, saved.getId());
    return saved;
  }

  @Transactional
  public void withdraw(Long playerId, Long nodeId, Map<String,Integer> troops){
    ResourceNode n = nodes.findById(nodeId).orElseThrow(() -> new IllegalArgumentException("Building not found"));
    String key = String.valueOf(playerId);
    Map<String,Integer> stack = n.getGarrison().get(key);
    if (stack == null || stack.isEmpty()) throw new IllegalStateException("You have no troops in this building");
    Map<String,Integer> leaving = (troops == null || troops.isEmpty()) ? new HashMap<>(stack) : troops;
    for (var e : leaving.entrySet()){
      String u = catalog.get(e.getKey()).getName();
      int have = stack.getOrDefault(u, 0);
      int take = Math.min(have, e.getValue());
      if (take <= 0) continue;
      if (take >= have) stack.remove(u); else stack.put(u, have - take);
    }
    if (stack.isEmpty()) n.getGarrison().remove(key);
    // building falls to UNCLAIMED only when NO player has troops left
    if (n.getGarrison().isEmpty()){
      n.setStatus(NodeStatus.UNCLAIMED); n.setControllingPlayerId(null);
      n.setControllingAllianceId(null); n.setControlSince(null); n.setOriginCityId(null);
    } else if (Objects.equals(n.getControllingPlayerId(), playerId)){
      // controller left but allies remain — hand control to a remaining holder (same alliance)
      Long next = n.getGarrison().keySet().stream().map(Long::valueOf).findFirst().orElse(null);
      n.setControllingPlayerId(next);
    }
    nodes.save(n);
    marchLeaving(playerId, n, leaving);
  }

  private void marchLeaving(Long playerId, ResourceNode n, Map<String,Integer> leaving){
    if (leaving.isEmpty()) return;
    City home = cities.findByPlayerIdAndCapitalTrue(playerId).orElseGet(() -> cities.findByPlayerId(playerId).get(0));
    Movement ret = new Movement();
    ret.setWorldId(home.getWorldId()); ret.setPlayerId(playerId); ret.setSourceCityId(home.getId());
    ret.setTargetNodeId(n.getId()); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(new HashMap<>(leaving));
    ret.setArriveAt(Instant.now().plusSeconds(travel.seconds(n.getIslandId(), home.getIslandId(), travel.slowestMinutesPerTile(leaving))));
    movements.save(ret);
  }

  // --- movement resolution (from TickScheduler) -------------------------------

  /** OCCUPY / SUPPORT arrives: take an uncontrolled building, or add to your stack in one you/your ally holds. */
  @Transactional
  public void resolveOccupy(Movement m, Instant now){
    ResourceNode n = nodes.findById(m.getTargetNodeId()).orElse(null);
    if (n == null){ heroRepo.findByActiveMovementId(m.getId()).ifPresent(h -> heroService.arriveHome(h, m.getSourceCityId())); return; }
    Long pid = m.getPlayerId();
    Long myAlliance = allianceOf(pid);
    boolean controlledByOther = n.getStatus() == NodeStatus.CONTROLLED
        && !sameAlliance(n.getControllingAllianceId(), myAlliance);
    if (controlledByOther){ sendHome(m, now); return; }   // enemy took it first — bounce back

    Map<String,Integer> stack = n.getGarrison().computeIfAbsent(String.valueOf(pid), k -> new HashMap<>());
    for (var e : m.getUnits().entrySet()) stack.merge(catalog.get(e.getKey()).getName(), e.getValue(), Integer::sum);
    if (n.getStatus() != NodeStatus.CONTROLLED){
      n.setStatus(NodeStatus.CONTROLLED);
      n.setControllingPlayerId(pid);
      n.setControllingAllianceId(myAlliance);
      n.setControlSince(now);
      n.setLastPayoutAt(now);
      n.setOriginCityId(m.getSourceCityId());
    }
    if (n.getClaimedAt() == null) n.setClaimedAt(now);
    n.setContestedUntil(null);
    nodes.save(n);
    missions.record(pid, MissionObjectiveType.OCCUPY_NODE, 1);
    heroRepo.findByActiveMovementId(m.getId()).ifPresent(h -> heroService.arriveHome(h, m.getSourceCityId()));
  }

  private void sendHome(Movement m, Instant now){
    Movement ret = new Movement();
    ret.setWorldId(m.getWorldId()); ret.setPlayerId(m.getPlayerId()); ret.setSourceCityId(m.getSourceCityId());
    ret.setTargetNodeId(m.getTargetNodeId()); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(new HashMap<>(m.getUnits()));
    ret.setArriveAt(now.plusSeconds(Math.max(5, now.getEpochSecond() - m.getDepartAt().getEpochSecond())));
    Movement saved = movements.save(ret);
    heroRepo.findByActiveMovementId(m.getId()).ifPresent(h -> { h.setActiveMovementId(saved.getId()); heroRepo.save(h); });
  }

  /** Apply combat to a controlled building on an enemy strike. Returns the (mutated) node saved. */
  @Transactional
  public void applyAttackResult(ResourceNode n, Long attackerId, Map<String,Integer> attackerSurvivors,
                                Map<String,Integer> defenderLostFlat, boolean attackerWon, Instant now){
    if (attackerWon){
      // seize: all defenders routed, attacker's survivors become the sole garrison, their alliance takes control
      n.getGarrison().clear();
      if (!attackerSurvivors.isEmpty()){
        n.getGarrison().put(String.valueOf(attackerId), new LinkedHashMap<>(attackerSurvivors));
        n.setStatus(NodeStatus.CONTROLLED);
        n.setControllingPlayerId(attackerId);
        n.setControllingAllianceId(allianceOf(attackerId));
        n.setControlSince(now);
        n.setLastPayoutAt(now);
      } else {
        n.setStatus(NodeStatus.UNCLAIMED);
        n.setControllingPlayerId(null); n.setControllingAllianceId(null); n.setControlSince(null);
      }
    } else {
      // repelled: spread the defender losses across the per-player stacks proportionally by unit
      spreadDefenderLosses(n, defenderLostFlat);
      if (n.getGarrison().isEmpty()){
        n.setStatus(NodeStatus.UNCLAIMED);
        n.setControllingPlayerId(null); n.setControllingAllianceId(null); n.setControlSince(null);
      }
    }
    nodes.save(n);
  }

  private void spreadDefenderLosses(ResourceNode n, Map<String,Integer> lostFlat){
    for (var loss : lostFlat.entrySet()){
      int remaining = loss.getValue() == null ? 0 : loss.getValue();
      String unit = loss.getKey();
      // total of this unit across stacks
      int total = 0;
      for (var s : n.getGarrison().values()) total += s.getOrDefault(unit, 0);
      if (total <= 0) continue;
      for (var s : n.getGarrison().values()){
        int have = s.getOrDefault(unit, 0);
        if (have <= 0) continue;
        int take = (int)Math.round((double) remaining * have / total);
        take = Math.min(take, have);
        if (take >= have) s.remove(unit); else s.put(unit, have - take);
      }
    }
    // drop any now-empty stacks
    n.getGarrison().entrySet().removeIf(e -> e.getValue().isEmpty());
  }

  public Map<String,Integer> flatGarrisonPublic(ResourceNode n){ return flatGarrison(n); }

  // --- 10-min payout sweep (split by troop share to controllers' cities) ------

  @Scheduled(fixedDelayString = "${polis.node-payout-ms:600000}")  // default 10 min
  @Transactional
  public void payoutSweep(){
    Instant now = Instant.now();
    for (ResourceNode n : nodes.findByStatus(NodeStatus.CONTROLLED)){
      Instant last = n.getLastPayoutAt() == null ? n.getControlSince() : n.getLastPayoutAt();
      if (last == null){ n.setLastPayoutAt(now); nodes.save(n); continue; }
      long elapsed = Math.max(0, now.getEpochSecond() - last.getEpochSecond());
      if (elapsed <= 0) continue;
      int pop = totalPop(n);
      if (pop <= 0){ n.setLastPayoutAt(now); nodes.save(n); continue; }

      // production for the elapsed window, split by each player's troop-pop share
      double produced = NodeRules.ratePerHour(n.getLevel(), pop) / 3600.0 * elapsed;
      ResourceType res = n.getNodeType().producedResource;
      for (var e : n.getGarrison().entrySet()){
        Long pid = Long.valueOf(e.getKey());
        double share = (double) stackPop(e.getValue()) / pop;
        long give = Math.round(produced * share);
        if (give <= 0) continue;
        City target = payCity(pid);
        if (target == null) continue;
        long cap = cityService.capacity(target.getId());
        switch (res){
          case WOOD  -> target.setWood(Math.min(cap, target.getWood() + give));
          case STONE -> target.setStone(Math.min(cap, target.getStone() + give));
          case WHEAT -> target.setWheat(Math.min(cap, target.getWheat() + give));
          default -> {}
        }
        cities.save(target);
      }
      // accrue control time for the tier gate (parallel-stacking: each held building counts)
      tierProgress.creditControlSeconds(n.getControllingAllianceId(), tierOf(n), elapsed);
      n.setLastPayoutAt(now);
      nodes.save(n);
    }
  }

  private City payCity(Long playerId){
    List<City> owned = cities.findByPlayerId(playerId);
    if (owned.isEmpty()) return null;
    return owned.stream().filter(City::isCapital).findFirst().orElse(owned.get(0));
  }
}
