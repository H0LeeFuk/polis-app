package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Endgame engine — the Wonders of the Aegean. During GROWTH the three Wonders sit DORMANT. Once the
 * world reaches a city/day threshold the world flips to ENDGAME and the Wonders become contestable.
 * Alliances seize them with troops (reusing the movement + combat systems), pool resources to raise
 * each from level 0 to {@link GameRules#WONDER_MAX_LEVEL}, and win by holding all three at max level
 * through an uninterrupted consolidation timer.
 */
@Service
public class WonderService {
  public static final long CONTESTED_WINDOW_SECONDS = 30 * 60;

  private final WonderRepo wonders;
  private final WorldStateRepo worldStates;
  private final WorldRepo worlds;
  private final CityRepo cities;
  private final PlayerRepo players;
  private final AllianceRepo alliances;
  private final IslandRepo islands;
  private final UnitCatalog catalog;
  private final TravelTimeService travel;
  private final BuildService build;
  private final CityService cityService;
  private final HeroService heroService;
  private final HeroRepo heroRepo;
  private final MovementRepo movements;
  private final CombatEngine combat;
  private final BattleReportService reports;

  public WonderService(WonderRepo wonders, WorldStateRepo worldStates, WorldRepo worlds, CityRepo cities,
                       PlayerRepo players, AllianceRepo alliances, IslandRepo islands, UnitCatalog catalog,
                       TravelTimeService travel, BuildService build, CityService cityService,
                       HeroService heroService, HeroRepo heroRepo, MovementRepo movements,
                       CombatEngine combat, BattleReportService reports){
    this.wonders=wonders; this.worldStates=worldStates; this.worlds=worlds; this.cities=cities;
    this.players=players; this.alliances=alliances; this.islands=islands; this.catalog=catalog;
    this.travel=travel; this.build=build; this.cityService=cityService; this.heroService=heroService;
    this.heroRepo=heroRepo; this.movements=movements; this.combat=combat; this.reports=reports;
  }

  // --- world state -----------------------------------------------------------

  @Transactional
  public WorldState stateFor(Long worldId){
    return worldStates.findByWorldId(worldId).orElseGet(() -> {
      WorldState s = new WorldState(); s.setWorldId(worldId); s.setPhase(WorldPhase.GROWTH);
      return worldStates.save(s);
    });
  }

  /** Background phase machine: trigger endgame, then evaluate the consolidation win timer. */
  @Scheduled(fixedDelayString = "${polis.wonder-sweep-ms:60000}")
  @Transactional
  public void sweep(){
    Instant now = Instant.now();
    for (World w : worlds.findAll()){
      WorldState s = stateFor(w.getId());
      if (s.getPhase() == WorldPhase.GROWTH){
        long cityCount = cities.countByWorldIdAndPlayerIdNotNull(w.getId());
        long days = Duration.between(w.getCreatedAt(), now).toDays();
        if (cityCount >= GameRules.ENDGAME_CITY_THRESHOLD || days >= GameRules.ENDGAME_DAYS){
          beginEndgame(s, now);
        }
      } else if (s.getPhase() == WorldPhase.ENDGAME){
        evaluateConsolidation(s, now);
      }
    }
  }

  private void beginEndgame(WorldState s, Instant now){
    s.setPhase(WorldPhase.ENDGAME); s.setEndgameStartedAt(now); worldStates.save(s);
    for (Wonder wo : wonders.findByWorldId(s.getWorldId())){
      if (wo.getStatus() == WonderStatus.DORMANT){ wo.setStatus(WonderStatus.ACTIVE); wo.setLastTickAt(now); wonders.save(wo); }
    }
  }

  /** DEV / admin: force a world into endgame immediately. */
  @Transactional
  public void forceEndgame(Long worldId){ beginEndgame(stateFor(worldId), Instant.now()); }

  /**
   * One alliance must hold ALL three Wonders, each at max level, uninterrupted. The timer starts
   * when that first becomes true, resets the moment it stops being true, and on completion the
   * world is WON.
   */
  private void evaluateConsolidation(WorldState s, Instant now){
    List<Wonder> all = wonders.findByWorldId(s.getWorldId());
    if (all.isEmpty()) return;
    Long holder = soleMaxHolder(all);
    if (holder == null){
      if (s.getConsolidationStartedAt() != null){ s.setConsolidationStartedAt(null); s.setConsolidationAllianceId(null); worldStates.save(s); }
      return;
    }
    if (!Objects.equals(holder, s.getConsolidationAllianceId()) || s.getConsolidationStartedAt() == null){
      s.setConsolidationAllianceId(holder); s.setConsolidationStartedAt(now); worldStates.save(s); return;
    }
    if (Duration.between(s.getConsolidationStartedAt(), now).getSeconds() >= GameRules.CONSOLIDATION_SECONDS){
      s.setPhase(WorldPhase.FINISHED); s.setWinnerAllianceId(holder); s.setFinishedAt(now);
      worldStates.save(s);
    }
  }

  /** The alliance that holds every Wonder at max level, or null if no single alliance does. */
  private Long soleMaxHolder(List<Wonder> all){
    Long ally = null;
    for (Wonder w : all){
      if (w.getLevel() < GameRules.WONDER_MAX_LEVEL || w.getControllingAllianceId() == null) return null;
      if (ally == null) ally = w.getControllingAllianceId();
      else if (!ally.equals(w.getControllingAllianceId())) return null;
    }
    return ally;
  }

  /** Resets the consolidation timer whenever a Wonder changes hands or regresses. */
  private void disturbConsolidation(Long worldId){
    WorldState s = stateFor(worldId);
    if (s.getConsolidationStartedAt() != null){ s.setConsolidationStartedAt(null); s.setConsolidationAllianceId(null); worldStates.save(s); }
  }

  // --- read DTOs -------------------------------------------------------------

  @Transactional
  public Map<String,Object> worldStateDto(Long worldId, Instant now){
    WorldState s = stateFor(worldId);
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("phase", s.getPhase().name());
    m.put("endgameStartedAt", s.getEndgameStartedAt()==null ? null : s.getEndgameStartedAt().toString());
    long days = Duration.between(worlds.findById(worldId).map(World::getCreatedAt).orElse(now), now).toDays();
    m.put("worldAgeDays", days);
    m.put("cityCount", cities.countByWorldIdAndPlayerIdNotNull(worldId));
    m.put("cityThreshold", GameRules.ENDGAME_CITY_THRESHOLD);
    m.put("daysThreshold", GameRules.ENDGAME_DAYS);
    // consolidation countdown
    Long winAlly = s.getConsolidationAllianceId();
    m.put("consolidationAllianceId", winAlly);
    m.put("consolidationAllianceName", winAlly==null ? null : alliances.findById(winAlly).map(Alliance::getName).orElse(null));
    long secsLeft = -1;
    if (s.getConsolidationStartedAt() != null)
      secsLeft = Math.max(0, GameRules.CONSOLIDATION_SECONDS - Duration.between(s.getConsolidationStartedAt(), now).getSeconds());
    m.put("consolidationSecondsLeft", secsLeft);
    m.put("consolidationTotalSeconds", GameRules.CONSOLIDATION_SECONDS);
    m.put("winnerAllianceId", s.getWinnerAllianceId());
    m.put("winnerAllianceName", s.getWinnerAllianceId()==null ? null : alliances.findById(s.getWinnerAllianceId()).map(Alliance::getName).orElse(null));
    m.put("wonders", wonders.findByWorldId(worldId).stream().map(w -> dto(w, now)).toList());
    return m;
  }

  public Map<String,Object> dto(Wonder w, Instant now){
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", w.getId());
    m.put("islandId", w.getIslandId());
    m.put("islandName", islands.findById(w.getIslandId()).map(Island::getName).orElse("?"));
    m.put("x", w.getX()); m.put("y", w.getY());
    m.put("kind", w.getWonderKind().name());
    m.put("name", w.getName());
    m.put("level", w.getLevel());
    m.put("maxLevel", GameRules.WONDER_MAX_LEVEL);
    m.put("status", w.getStatus().name());
    m.put("controllingAllianceId", w.getControllingAllianceId());
    m.put("controllingAllianceName", w.getControllingAllianceId()==null ? null :
        alliances.findById(w.getControllingAllianceId()).map(Alliance::getName).orElse(null));
    m.put("controllingPlayerName", w.getControllingPlayerId()==null ? null :
        players.findById(w.getControllingPlayerId()).map(Player::getUsername).orElse(null));
    m.put("garrison", w.getGarrison());
    m.put("garrisonPop", garrisonPop(w.getGarrison()));
    long nextCost = w.getLevel() < GameRules.WONDER_MAX_LEVEL ? GameRules.wonderLevelCost(w.getLevel()+1) : 0;
    m.put("nextLevelCost", nextCost);
    m.put("investedWood", w.getInvestedWood());
    m.put("investedStone", w.getInvestedStone());
    m.put("investedWheat", w.getInvestedWheat());
    m.put("contestedUntil", w.getContestedUntil()==null ? null : w.getContestedUntil().toString());
    return m;
  }

  /** Alliance leaderboard: ranked by wonders held, then total wonder levels. */
  @Transactional
  public List<Map<String,Object>> leaderboard(Long worldId){
    Map<Long,int[]> byAlly = new HashMap<>();   // allianceId -> [held, totalLevels]
    for (Wonder w : wonders.findByWorldId(worldId)){
      if (w.getControllingAllianceId() == null) continue;
      int[] v = byAlly.computeIfAbsent(w.getControllingAllianceId(), k -> new int[2]);
      v[0]++; v[1] += w.getLevel();
    }
    return byAlly.entrySet().stream()
        .sorted((a,b) -> b.getValue()[0]!=a.getValue()[0] ? b.getValue()[0]-a.getValue()[0] : b.getValue()[1]-a.getValue()[1])
        .map(e -> {
          Map<String,Object> m = new LinkedHashMap<>();
          m.put("allianceId", e.getKey());
          m.put("allianceName", alliances.findById(e.getKey()).map(Alliance::getName).orElse("?"));
          m.put("wondersHeld", e.getValue()[0]);
          m.put("totalLevels", e.getValue()[1]);
          return m;
        }).toList();
  }

  private int garrisonPop(Map<String,Integer> g){
    int p=0; for (var e: g.entrySet()) p += catalog.get(e.getKey()).getPopulationCost() * e.getValue(); return p;
  }

  // --- player actions: dispatch troop movements ------------------------------

  @Transactional
  public Movement occupy(Long playerId, Long wonderId, Long cityId, Map<String,Integer> troops, Long heroId){
    Wonder w = require(wonderId);
    requireEndgame(w.getWorldId());
    if (w.getStatus() == WonderStatus.DORMANT) throw new IllegalStateException("This Wonder is not yet active");
    if (w.getStatus() == WonderStatus.CONTROLLED && !sameAlliance(playerId, w.getControllingAllianceId()))
      throw new IllegalStateException("That Wonder is controlled — attack it instead");
    return dispatch(playerId, cityId, w, troops, MovementPhase.OCCUPY, heroId);
  }

  @Transactional
  public Movement attack(Long playerId, Long wonderId, Long cityId, Map<String,Integer> troops, Long heroId){
    Wonder w = require(wonderId);
    requireEndgame(w.getWorldId());
    if (w.getStatus() == WonderStatus.DORMANT) throw new IllegalStateException("This Wonder is not yet active");
    if (sameAlliance(playerId, w.getControllingAllianceId())) throw new IllegalStateException("Your alliance already holds that Wonder");
    return dispatch(playerId, cityId, w, troops, MovementPhase.OUT, heroId);
  }

  private Movement dispatch(Long playerId, Long cityId, Wonder w, Map<String,Integer> troops,
                            MovementPhase phase, Long heroId){
    City src = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(src.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    if (troops == null || troops.isEmpty()) throw new IllegalArgumentException("Select at least one unit");
    Hero hero = heroId != null ? heroService.requireOwned(playerId, heroId) : null;
    int heroLoad = hero != null ? travel.heroLandLoad(hero.getRace()) : 0;
    travel.requireTransport(troops, src.getIslandId(), w.getIslandId(), heroLoad);
    build.deductGarrison(cityId, troops);
    boolean water = travel.crossesWater(src.getIslandId(), w.getIslandId());
    long secs = travel.seconds(src.getIslandId(), w.getIslandId(), travel.effectiveMinutesPerTile(troops, water));
    if (hero != null) secs = (long)(secs * heroService.travelMult(hero));
    Movement m = new Movement();
    m.setWorldId(src.getWorldId()); m.setPlayerId(playerId); m.setSourceCityId(cityId);
    m.setTargetWonderId(w.getId()); m.setPhase(phase);
    m.setUnits(new HashMap<>(troops)); m.setArriveAt(Instant.now().plusSeconds(Math.max(5, secs)));
    Movement saved = movements.save(m);
    if (heroId != null) heroService.sendHero(playerId, heroId, cityId, saved.getId());
    return saved;
  }

  @Transactional
  public void withdraw(Long playerId, Long wonderId, Map<String,Integer> troops){
    Wonder w = require(wonderId);
    if (!sameAlliance(playerId, w.getControllingAllianceId())) throw new IllegalStateException("Your alliance does not hold that Wonder");
    Map<String,Integer> leaving = (troops == null || troops.isEmpty()) ? new HashMap<>(w.getGarrison()) : troops;
    for (var e : leaving.entrySet()){
      String k = catalog.get(e.getKey()).getName();
      int have = w.getGarrison().getOrDefault(k, 0);
      int take = Math.min(have, e.getValue());
      if (take <= 0) continue;
      if (take >= have) w.getGarrison().remove(k); else w.getGarrison().put(k, have - take);
    }
    if (w.getGarrison().isEmpty()){
      w.setStatus(WonderStatus.ACTIVE); w.setControllingPlayerId(null); w.setControllingAllianceId(null);
      disturbConsolidation(w.getWorldId());
    }
    wonders.save(w);
    City home = cities.findByPlayerIdAndCapitalTrue(playerId).orElseGet(() -> cities.findByPlayerId(playerId).get(0));
    Movement ret = new Movement();
    ret.setWorldId(home.getWorldId()); ret.setPlayerId(playerId); ret.setSourceCityId(home.getId());
    ret.setTargetWonderId(wonderId); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(new HashMap<>(leaving));
    ret.setArriveAt(Instant.now().plusSeconds(travel.seconds(w.getIslandId(), home.getIslandId(), travel.slowestMinutesPerTile(leaving))));
    movements.save(ret);
  }

  // --- investment: pool resources to raise a Wonder's level ------------------

  @Transactional
  public Map<String,Object> invest(Long playerId, Long wonderId, Long cityId, long each){
    Wonder w = require(wonderId);
    if (w.getStatus() != WonderStatus.CONTROLLED) throw new IllegalStateException("The Wonder must be securely held to invest");
    if (!sameAlliance(playerId, w.getControllingAllianceId())) throw new IllegalStateException("Only the controlling alliance may invest");
    if (w.getLevel() >= GameRules.WONDER_MAX_LEVEL) throw new IllegalStateException("This Wonder is already at maximum level");
    if (each <= 0) throw new IllegalArgumentException("Enter an amount to invest");
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    cityService.sync(c);
    if (c.getWood() < each || c.getStone() < each || c.getWheat() < each)
      throw new IllegalStateException("Not enough resources — need " + each + " of each");
    c.setWood(c.getWood()-each); c.setStone(c.getStone()-each); c.setWheat(c.getWheat()-each);
    cities.save(c);
    w.setInvestedWood(w.getInvestedWood()+each);
    w.setInvestedStone(w.getInvestedStone()+each);
    w.setInvestedWheat(w.getInvestedWheat()+each);
    // consume full levels while the pool covers the next-level cost
    while (w.getLevel() < GameRules.WONDER_MAX_LEVEL){
      long cost = GameRules.wonderLevelCost(w.getLevel()+1);
      if (w.getInvestedWood() < cost || w.getInvestedStone() < cost || w.getInvestedWheat() < cost) break;
      w.setInvestedWood(w.getInvestedWood()-cost);
      w.setInvestedStone(w.getInvestedStone()-cost);
      w.setInvestedWheat(w.getInvestedWheat()-cost);
      w.setLevel(w.getLevel()+1);
    }
    wonders.save(w);
    return dto(w, Instant.now());
  }

  // --- movement resolution (called from TickScheduler) -----------------------

  /** OCCUPY arrives: claim an ACTIVE/CONTESTED Wonder, or reinforce one your alliance holds. */
  @Transactional
  public void resolveOccupy(Movement m, Instant now){
    Wonder w = wonders.findById(m.getTargetWonderId()).orElse(null);
    if (w == null){ heroRepo.findByActiveMovementId(m.getId()).ifPresent(h -> heroService.arriveHome(h, m.getSourceCityId())); return; }
    boolean mine = sameAlliance(m.getPlayerId(), w.getControllingAllianceId());
    if (w.getStatus() == WonderStatus.CONTROLLED && !mine){ sendHome(m, now); return; }
    for (var e : m.getUnits().entrySet()) w.getGarrison().merge(catalog.get(e.getKey()).getName(), e.getValue(), Integer::sum);
    boolean wasControlled = w.getStatus() == WonderStatus.CONTROLLED;
    w.setStatus(WonderStatus.CONTROLLED);
    w.setControllingPlayerId(m.getPlayerId());
    w.setControllingAllianceId(players.findById(m.getPlayerId()).map(Player::getAllianceId).orElse(null));
    if (w.getClaimedAt() == null) w.setClaimedAt(now);
    w.setContestedUntil(null);
    wonders.save(w);
    if (!wasControlled) disturbConsolidation(w.getWorldId());   // new claim → recompute the win timer
    heroRepo.findByActiveMovementId(m.getId()).ifPresent(h -> heroService.arriveHome(h, m.getSourceCityId()));
  }

  /** OUT assault arrives: fight the Wonder garrison; a win drops its level and opens it as CONTESTED. */
  @Transactional
  public void resolveAttack(Movement m, Instant now){
    Wonder w = wonders.findById(m.getTargetWonderId()).orElse(null);
    if (w == null){ returnArmy(m, m.getUnits()); return; }

    Map<String,Integer> attackerSent = new LinkedHashMap<>(m.getUnits());
    Map<String,Integer> defenderPresent = new LinkedHashMap<>(w.getGarrison());

    // two-layer combat: a SEA assault hits a sea garrison, a LAND assault a land garrison
    CombatLayer layer = combat.attackLayer(attackerSent);
    if (layer == null) layer = CombatLayer.LAND;
    Map<String,Integer> attackerCombat = combat.combatants(attackerSent, layer);
    Map<String,Integer> defenderCombat = combat.combatants(defenderPresent, layer);

    Hero attackerHero = heroRepo.findByActiveMovementId(m.getId()).orElse(null);
    CombatEngine.Mods mods = attackerHero != null ? heroService.offenseMods(attackerHero) : CombatEngine.Mods.none();
    CombatEngine.CombatFx fx = attackerHero != null ? heroService.combatFx(attackerHero) : CombatEngine.CombatFx.none();
    City src = cities.findById(m.getSourceCityId()).orElse(null);
    Element element = src!=null && src.getRace()!=null ? src.getRace().element : Element.FIRE;
    double heroAtk = attackerHero != null ? heroService.baseAttack(attackerHero) : 0;
    CombatEngine.Result r = combat.resolve(attackerCombat, element, defenderCombat, mods, fx, heroAtk);

    Long formerAlliance = w.getControllingAllianceId();
    // surviving defenders = this layer's survivors + the untouched other-layer garrison
    Map<String,Integer> newGarrison = new LinkedHashMap<>(r.defenderSurvived());
    for (var e : defenderPresent.entrySet())
      if (!defenderCombat.containsKey(e.getKey())) newGarrison.merge(e.getKey(), e.getValue(), Integer::sum);

    if (r.outcome() == BattleOutcome.VICTORY){
      w.setGarrison(newGarrison);
      // regression: a captured Wonder loses levels and falls open
      w.setLevel(Math.max(0, w.getLevel() - GameRules.WONDER_REGRESS_PER_LOSS));
      if (newGarrison.isEmpty()){
        w.setStatus(WonderStatus.CONTESTED);
        w.setControllingPlayerId(null); w.setControllingAllianceId(null);
        w.setContestedUntil(now.plusSeconds(CONTESTED_WINDOW_SECONDS));
      }
      // reset pooled investment toward the (now lost) next level
      w.setInvestedWood(0); w.setInvestedStone(0); w.setInvestedWheat(0);
      disturbConsolidation(w.getWorldId());
    } else {
      w.setGarrison(newGarrison);
    }
    wonders.save(w);

    HeroParticipation hp = resolveAttackerHero(attackerHero, r, now);
    reports.createNodeReport(m, new BattleResult(r.outcome(), attackerCombat, r.attackerLost(), r.attackerSurvived(),
        defenderCombat, r.defenderLost(), r.defenderSurvived(),
        Map.of("WOOD",0L,"STONE",0L,"WHEAT",0L), r.attackerAttackPower(), r.defenderDefencePower(), r.siegeDamage(),
        r.attackByElement(), r.defenseByElement()),
        hp, "Wonder: " + w.getName(), formerAlliance);

    // survivors + cargo march home
    Map<String,Integer> returnUnits = new LinkedHashMap<>(r.attackerSurvived());
    for (var e : attackerSent.entrySet())
      if (!attackerCombat.containsKey(e.getKey())) returnUnits.merge(e.getKey(), e.getValue(), Integer::sum);
    if (returnUnits.isEmpty() && attackerHero == null) return;
    Movement ret = new Movement();
    ret.setWorldId(m.getWorldId()); ret.setPlayerId(m.getPlayerId()); ret.setSourceCityId(m.getSourceCityId());
    ret.setTargetWonderId(m.getTargetWonderId()); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(returnUnits);
    ret.setArriveAt(now.plusSeconds(Math.max(5, now.getEpochSecond() - m.getDepartAt().getEpochSecond())));
    Movement savedRet = movements.save(ret);
    if (attackerHero != null){ attackerHero.setActiveMovementId(savedRet.getId()); heroRepo.save(attackerHero); }
  }

  private HeroParticipation resolveAttackerHero(Hero hero, CombatEngine.Result r, Instant now){
    if (hero == null) return null;
    int bonusPct = heroService.attackBonusPct(hero);
    int lossRedPct = heroService.lossReductionPct(hero);
    String skillUsed = heroService.consumeArmedSkill(hero, now);
    int xp = 0; Integer leveledTo = null;
    if (r.outcome() == BattleOutcome.VICTORY){ xp = Math.max(1, r.defenderDefencePower()/10); leveledTo = heroService.grantXp(hero, xp); }
    heroRepo.save(hero);
    return new HeroParticipation(hero.getName(), hero.getLevel(), bonusPct, lossRedPct, skillUsed, xp, leveledTo, false);
  }

  private void sendHome(Movement m, Instant now){
    Movement ret = new Movement();
    ret.setWorldId(m.getWorldId()); ret.setPlayerId(m.getPlayerId()); ret.setSourceCityId(m.getSourceCityId());
    ret.setTargetWonderId(m.getTargetWonderId()); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(new HashMap<>(m.getUnits()));
    ret.setArriveAt(now.plusSeconds(Math.max(5, now.getEpochSecond() - m.getDepartAt().getEpochSecond())));
    Movement saved = movements.save(ret);
    heroRepo.findByActiveMovementId(m.getId()).ifPresent(h -> { h.setActiveMovementId(saved.getId()); heroRepo.save(h); });
  }

  private void returnArmy(Movement m, Map<String,Integer> army){
    Movement ret = new Movement();
    ret.setWorldId(m.getWorldId()); ret.setPlayerId(m.getPlayerId()); ret.setSourceCityId(m.getSourceCityId());
    ret.setTargetWonderId(m.getTargetWonderId()); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(new HashMap<>(army)); ret.setArriveAt(Instant.now().plusSeconds(5));
    movements.save(ret);
  }

  // --- helpers ---------------------------------------------------------------

  private Wonder require(Long wonderId){
    return wonders.findById(wonderId).orElseThrow(() -> new IllegalArgumentException("Wonder not found"));
  }
  private void requireEndgame(Long worldId){
    if (stateFor(worldId).getPhase() != WorldPhase.ENDGAME) throw new IllegalStateException("The endgame has not begun");
  }
  private boolean sameAlliance(Long playerId, Long allianceId){
    if (allianceId == null) return false;
    return players.findById(playerId).map(p -> allianceId.equals(p.getAllianceId())).orElse(false);
  }
}
