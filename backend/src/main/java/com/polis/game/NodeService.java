package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Contested resource nodes: lazy production, occupy/reinforce/withdraw/attack via TroopMovement,
 * a periodic delivery sweep into the controlling alliance's treasury, and troop-weighted rare
 * drops. Combat reuses {@link CombatEngine} + hero bonuses, exactly like city raids.
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
  private final HeroItemRepo heroItems;
  private final ItemFactory itemFactory;
  private final MovementRepo movements;
  private final MissionService missions;
  private final Random rnd = new Random();

  public NodeService(ResourceNodeRepo nodes, CityRepo cities, PlayerRepo players, AllianceRepo alliances,
                     IslandRepo islands, UnitCatalog catalog, TravelTimeService travel, BuildService build,
                     HeroService heroService, HeroRepo heroRepo, HeroItemRepo heroItems, ItemFactory itemFactory,
                     MovementRepo movements, MissionService missions){
    this.nodes=nodes; this.cities=cities; this.players=players; this.alliances=alliances; this.islands=islands;
    this.catalog=catalog; this.travel=travel; this.build=build; this.heroService=heroService; this.heroRepo=heroRepo;
    this.heroItems=heroItems; this.itemFactory=itemFactory; this.movements=movements; this.missions=missions;
  }

  // --- production (lazy catch-up) ---------------------------------------------

  /** Accrue production up to now based on garrison vs cap; UNCLAIMED nodes produce nothing. */
  @Transactional
  public ResourceNode settle(ResourceNode n, Instant now){
    long elapsed = Math.max(0, now.getEpochSecond() - n.getLastTickAt().getEpochSecond());
    if (elapsed > 0 && n.getStatus() == NodeStatus.CONTROLLED){
      double rate = NodeRules.ratePerHour(n.getLevel(), garrisonPop(n.getGarrison()));
      n.setAccumulatedResources(n.getAccumulatedResources() + Math.round(rate / 3600.0 * elapsed));
    }
    n.setLastTickAt(now);
    return n;
  }

  private int garrisonPop(Map<String,Integer> g){
    int p = 0; for (var e : g.entrySet()) p += catalog.get(e.getKey()).getPopulationCost() * e.getValue(); return p;
  }

  // --- read endpoints ---------------------------------------------------------

  @Transactional
  public List<Map<String,Object>> islandNodes(Long islandId){
    Instant now = Instant.now();
    List<Map<String,Object>> out = new ArrayList<>();
    for (ResourceNode n : nodes.findByIslandId(islandId)){ settle(n, now); nodes.save(n); out.add(dto(n)); }
    return out;
  }

  @Transactional
  public Map<String,Object> node(Long nodeId){
    ResourceNode n = nodes.findById(nodeId).orElseThrow(() -> new IllegalArgumentException("Node not found"));
    settle(n, Instant.now()); nodes.save(n);
    return dto(n);
  }

  @Transactional
  public List<Map<String,Object>> myNodes(Long playerId){
    Instant now = Instant.now();
    List<Map<String,Object>> out = new ArrayList<>();
    for (ResourceNode n : nodes.findByControllingPlayerId(playerId)){ settle(n, now); nodes.save(n); out.add(dto(n)); }
    return out;
  }

  public Map<String,Object> dto(ResourceNode n){
    int cap = NodeRules.garrisonCap(n.getLevel());
    int pop = garrisonPop(n.getGarrison());
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", n.getId());
    m.put("islandId", n.getIslandId());
    m.put("islandName", islands.findById(n.getIslandId()).map(Island::getName).orElse("?"));
    m.put("x", n.getX()); m.put("y", n.getY());
    m.put("nodeType", n.getNodeType().name());
    m.put("producedResource", n.getNodeType().producedResource.name());
    m.put("level", n.getLevel());
    m.put("status", n.getStatus().name());
    m.put("controllingPlayerId", n.getControllingPlayerId());
    m.put("controllingPlayerName", n.getControllingPlayerId()==null ? null :
        players.findById(n.getControllingPlayerId()).map(Player::getUsername).orElse("Unknown"));
    m.put("controllingAllianceId", n.getControllingAllianceId());
    m.put("controllingAllianceName", n.getControllingAllianceId()==null ? null :
        alliances.findById(n.getControllingAllianceId()).map(Alliance::getName).orElse(null));
    m.put("garrison", n.getGarrison());
    m.put("garrisonPop", pop);
    m.put("garrisonCap", cap);
    m.put("accumulated", n.getAccumulatedResources());
    m.put("ratePerHour", Math.round(NodeRules.ratePerHour(n.getLevel(), pop)));
    m.put("contestedUntil", n.getContestedUntil()==null ? null : n.getContestedUntil().toString());
    m.put("name", label(n));
    return m;
  }

  public String label(ResourceNode n){
    String t = switch (n.getNodeType()){
      case SACRED_GROVE -> "Sacred Grove"; case MARBLE_QUARRY -> "Marble Quarry"; case WHEAT_FIELD -> "Wheat Field";
    };
    return t + " — Lv " + n.getLevel();
  }

  // --- player actions (dispatch troop movements) ------------------------------

  @Transactional
  public Movement occupy(Long playerId, Long nodeId, Long cityId, Map<String,Integer> troops, Long heroId){
    ResourceNode n = nodes.findById(nodeId).orElseThrow(() -> new IllegalArgumentException("Node not found"));
    if (n.getStatus() == NodeStatus.CONTROLLED)
      throw new IllegalStateException("That node is controlled — attack it instead");
    return dispatch(playerId, cityId, n, troops, MovementPhase.OCCUPY, heroId);
  }

  @Transactional
  public Movement reinforce(Long playerId, Long nodeId, Long cityId, Map<String,Integer> troops){
    ResourceNode n = nodes.findById(nodeId).orElseThrow(() -> new IllegalArgumentException("Node not found"));
    if (!Objects.equals(n.getControllingPlayerId(), playerId))
      throw new IllegalStateException("You do not control that node");
    return dispatch(playerId, cityId, n, troops, MovementPhase.OCCUPY, null);
  }

  @Transactional
  public Movement attack(Long playerId, Long nodeId, Long cityId, Map<String,Integer> troops, Long heroId){
    ResourceNode n = nodes.findById(nodeId).orElseThrow(() -> new IllegalArgumentException("Node not found"));
    if (Objects.equals(n.getControllingPlayerId(), playerId))
      throw new IllegalStateException("You already control that node");
    return dispatch(playerId, cityId, n, troops, MovementPhase.OUT, heroId);
  }

  private Movement dispatch(Long playerId, Long cityId, ResourceNode n, Map<String,Integer> troops,
                            MovementPhase phase, Long heroId){
    City src = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(src.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    if (troops == null || troops.isEmpty()) throw new IllegalArgumentException("Select at least one unit");
    // LAND troops — and a land (non-flying/swimming) hero — crossing open water need transport ships
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
    ResourceNode n = nodes.findById(nodeId).orElseThrow(() -> new IllegalArgumentException("Node not found"));
    if (!Objects.equals(n.getControllingPlayerId(), playerId)) throw new IllegalStateException("You do not control that node");
    Map<String,Integer> leaving = (troops == null || troops.isEmpty()) ? new HashMap<>(n.getGarrison()) : troops;
    // reduce garrison
    for (var e : leaving.entrySet()){
      String k = catalog.get(e.getKey()).getName();
      int have = n.getGarrison().getOrDefault(k, 0);
      int take = Math.min(have, e.getValue());
      if (take <= 0) continue;
      if (take >= have) n.getGarrison().remove(k); else n.getGarrison().put(k, have - take);
    }
    if (n.getGarrison().isEmpty()){ n.setStatus(NodeStatus.UNCLAIMED); n.setControllingPlayerId(null); n.setControllingAllianceId(null); }
    nodes.save(n);
    // march survivors home to the player's capital
    City home = cities.findByPlayerIdAndCapitalTrue(playerId).orElseGet(() -> cities.findByPlayerId(playerId).get(0));
    Movement ret = new Movement();
    ret.setWorldId(home.getWorldId()); ret.setPlayerId(playerId); ret.setSourceCityId(home.getId());
    ret.setTargetNodeId(nodeId); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(new HashMap<>(leaving));
    ret.setArriveAt(Instant.now().plusSeconds(travel.seconds(n.getIslandId(), home.getIslandId(), travel.slowestMinutesPerTile(leaving))));
    movements.save(ret);
  }

  // --- movement resolution (called from TickScheduler) ------------------------

  /** OCCUPY movement arrives: claim an UNCLAIMED/CONTESTED node, or reinforce one you hold. */
  @Transactional
  public void resolveOccupy(Movement m, Instant now){
    ResourceNode n = nodes.findById(m.getTargetNodeId()).orElse(null);
    if (n == null){ heroRepo.findByActiveMovementId(m.getId()).ifPresent(h -> heroService.arriveHome(h, m.getSourceCityId())); return; }
    settle(n, now);
    boolean mine = Objects.equals(n.getControllingPlayerId(), m.getPlayerId());
    if (n.getStatus() == NodeStatus.CONTROLLED && !mine){
      // someone else took it first — bounce the troops back home
      sendHome(m, now); return;
    }
    for (var e : m.getUnits().entrySet()) n.getGarrison().merge(catalog.get(e.getKey()).getName(), e.getValue(), Integer::sum);
    n.setStatus(NodeStatus.CONTROLLED);
    n.setControllingPlayerId(m.getPlayerId());
    n.setControllingAllianceId(players.findById(m.getPlayerId()).map(Player::getAllianceId).orElse(null));
    if (n.getClaimedAt() == null) n.setClaimedAt(now);
    n.setContestedUntil(null);
    nodes.save(n);
    missions.record(m.getPlayerId(), MissionObjectiveType.OCCUPY_NODE, 1);
    // the hero, if it escorted the occupation, garrisons with the troops then is freed back home next tick
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

  // --- delivery sweep + rare drops --------------------------------------------

  @Scheduled(fixedDelayString = "${polis.node-sweep-ms:1800000}")  // default 30 min
  @Transactional
  public void deliverySweep(){
    Instant now = Instant.now();
    for (ResourceNode n : nodes.findByStatus(NodeStatus.CONTROLLED)){
      settle(n, now);
      Long pid = n.getControllingPlayerId();
      if (pid == null){ nodes.save(n); continue; }
      // recompute delivery target from the controller's CURRENT alliance
      Long allianceId = players.findById(pid).map(Player::getAllianceId).orElse(null);
      n.setControllingAllianceId(allianceId);
      long amount = n.getAccumulatedResources();
      if (amount > 0 && allianceId != null){
        alliances.findById(allianceId).ifPresent(a -> {
          switch (n.getNodeType().producedResource){
            case WOOD  -> a.setTreasuryWood(a.getTreasuryWood() + amount);
            case STONE -> a.setTreasuryStone(a.getTreasuryStone() + amount);
            case WHEAT -> a.setTreasuryWheat(a.getTreasuryWheat() + amount);
            default -> {}
          }
          alliances.save(a);
        });
        n.setAccumulatedResources(0);   // delivered
      } // else: no alliance → hold at node until they join one
      // resource nodes no longer drop relics — only island bosses do
      nodes.save(n);
    }
  }

  private void rollDrop(ResourceNode n, Long playerId){
    int guardingPop = garrisonPop(n.getGarrison());
    double itemBonus = 0.0; // optional hero drop-chance buff; left neutral
    double chance = NodeRules.dropChance(guardingPop, itemBonus);
    if (rnd.nextDouble() < chance){
      heroItems.save(itemFactory.roll(playerId, rnd));
    }
  }
}
