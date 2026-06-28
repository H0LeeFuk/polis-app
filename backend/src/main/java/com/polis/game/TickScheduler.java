package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Background loop that advances the world: completes due build/training jobs and
 * resolves arriving movements (colony foundings, raids, returns). Players also get
 * their own city synced on every read, so this mainly drives offline/background cities.
 */
@Component
public class TickScheduler {
  private final CityService cityService;
  private final CityFactory cityFactory;
  private final CityRepo cities; private final UnitRepo units; private final JobRepo jobs;
  private final MovementRepo movements; private final PlayerRepo players;
  private final BattleReportService reports;
  private final CombatEngine combat;
  private final UnitCatalog catalog;
  private final HeroService heroService;
  private final HeroRepo heroRepo;
  private final NodeService nodeService;
  private final ResourceNodeRepo resourceNodes;
  private final SettleService settleService;
  private final LibraryService library;

  public TickScheduler(CityService cityService, CityFactory cityFactory, CityRepo cities, UnitRepo units,
                       JobRepo jobs, MovementRepo movements, PlayerRepo players, BattleReportService reports,
                       CombatEngine combat, UnitCatalog catalog, HeroService heroService, HeroRepo heroRepo,
                       NodeService nodeService, ResourceNodeRepo resourceNodes, SettleService settleService,
                       LibraryService library){
    this.cityService=cityService; this.cityFactory=cityFactory; this.cities=cities; this.units=units;
    this.jobs=jobs; this.movements=movements; this.players=players; this.reports=reports;
    this.combat=combat; this.catalog=catalog; this.heroService=heroService; this.heroRepo=heroRepo;
    this.nodeService=nodeService; this.resourceNodes=resourceNodes; this.settleService=settleService;
    this.library=library;
  }

  @Scheduled(fixedDelayString = "${polis.tick.interval-ms}")
  @Transactional
  public void tick(){
    Instant now = Instant.now();

    // finalize due construction/training across all cities
    Set<Long> touched = new HashSet<>();
    for (BuildJob j : jobs.findDueActiveJobs(now)) touched.add(j.getCityId());
    for (Long cid : touched) cities.findById(cid).ifPresent(c -> cityService.finalizeJobs(c, now));

    // heroes whose recovery time has elapsed return to duty
    heroService.recoverHealed(now);
    // complete any due Library research
    library.completeDue(now);

    // resolve movements
    for (Movement m : movements.findDue(now)){
      // SETTLE parks on arrival awaiting the race choice — it is NOT marked resolved here.
      if (m.getPhase() == MovementPhase.SETTLE){ settleService.onArrive(m, now); continue; }
      switch (m.getPhase()){
        case COLONY -> resolveColony(m);
        case OUT     -> { if (m.getTargetNodeId()!=null) resolveNodeAttack(m, now); else resolveRaid(m, now); }
        case RETURN  -> resolveReturn(m);
        case OCCUPY  -> nodeService.resolveOccupy(m, now);
        default      -> {}
      }
      m.setResolved(true); movements.save(m);
    }

    // foundings the player never completed: send the hero home, free the slot
    settleService.releaseAbandoned(now);
  }

  private void resolveColony(Movement m){
    if (cities.findByIslandIdAndSlot(m.getTargetIslandId(), m.getTargetSlot()).isPresent()) return; // plot taken
    Player p = players.findById(m.getPlayerId()).orElse(null); if (p==null) return;
    String base = cities.findByPlayerIdAndCapitalTrue(p.getId()).map(City::getName).orElse(p.getUsername());
    cityFactory.createPlayerCity(m.getWorldId(), p.getId(), m.getTargetIslandId(), m.getTargetSlot(),
        base.split(" ")[0] + " Colony", false);
  }

  private void resolveRaid(Movement m, Instant now){
    City tgt = cities.findById(m.getTargetCityId()).orElse(null);
    if (tgt == null) { returnArmy(m, m.getUnits(), null); return; }

    Map<String,Integer> attackerSent = new LinkedHashMap<>(m.getUnits());

    // defender garrison snapshot: the units stationed at the target right now
    List<CityUnit> garrison = units.findByCityId(tgt.getId());
    Map<String,Integer> defenderPresent = new LinkedHashMap<>();
    for (CityUnit cu : garrison)
      if (cu.getCount() > 0) defenderPresent.merge(cu.getType().toUpperCase(), cu.getCount(), Integer::sum);

    // hero modifiers: the attacker's marching hero, plus the defender's idle stationed hero
    Hero attackerHero = heroRepo.findByActiveMovementId(m.getId()).orElse(null);
    Hero defenderHero = tgt.getPlayerId()==null ? null :
        heroRepo.findByStationedCityIdAndStateAndUnlockedTrue(tgt.getId(), HeroState.IDLE).stream().findFirst().orElse(null);
    CombatEngine.Mods mods = combinedMods(attackerHero, defenderHero);
    // stacking order: race → Library → hero (hero already folded into combinedMods)
    City srcCity = cities.findById(m.getSourceCityId()).orElse(null);
    Race atkRace = srcCity!=null ? srcCity.getRace() : null;
    LibraryService.LibEffects atkLib = srcCity!=null ? library.effects(srcCity.getId()) : LibraryService.LibEffects.none();
    LibraryService.LibEffects defLib = library.effects(tgt.getId());
    mods = new CombatEngine.Mods(
        mods.attackMult() * (atkRace!=null ? atkRace.attackMult : 1.0) * atkLib.attackMult(),
        mods.defenseMult() * (tgt.getRace()!=null ? tgt.getRace().defenseMult : 1.0) * defLib.defenseMult(),
        mods.sharpDefenseMult() * defLib.sharpDefenseMult(), mods.attackerLossMult());

    CombatEngine.Result r = combat.resolve(attackerSent, defenderPresent, mods);
    BattleOutcome outcome = r.outcome();

    // apply defender casualties back to the garrison
    for (CityUnit cu : garrison){
      int lost = r.defenderLost().getOrDefault(cu.getType().toUpperCase(), 0);
      if (lost > 0){ cu.setCount(Math.max(0, cu.getCount()-lost)); units.save(cu); }
    }

    // plunder: victory only — surviving attackers carry off resources up to their carry capacity
    Map<String,Long> resourcesStolen = new LinkedHashMap<>();
    resourcesStolen.put("WOOD",0L); resourcesStolen.put("STONE",0L); resourcesStolen.put("SILVER",0L);
    Map<String,Long> loot = null;
    if (outcome == BattleOutcome.VICTORY){
      long carry = armyCarry(r.attackerSurvived());
      if (atkRace != null) carry = (long)(carry * atkRace.lootMult);                    // race loot bonus
      carry = (long)(carry * atkLib.lootMult());                                        // Library loot research
      if (attackerHero != null) carry = (long)(carry * heroService.lootMult(attackerHero)); // Cunning
      long pool = (long)(tgt.getWood()+tgt.getStone()+tgt.getSilver());
      long want = Math.min(carry, pool);
      if (want > 0 && pool > 0){
        long lw=(long)(want*tgt.getWood()/pool), ls=(long)(want*tgt.getStone()/pool), lv=(long)(want*tgt.getSilver()/pool);
        tgt.setWood(tgt.getWood()-lw); tgt.setStone(tgt.getStone()-ls); tgt.setSilver(tgt.getSilver()-lv);
        resourcesStolen.put("WOOD",lw); resourcesStolen.put("STONE",ls); resourcesStolen.put("SILVER",lv);
        loot = new HashMap<>(resourcesStolen);
      }
    }
    // power still tracks the beating a city has taken (drives barbarian difficulty/score)
    tgt.setPower(outcome == BattleOutcome.VICTORY ? Math.max(40, tgt.getPower()*0.45+20) : Math.max(40, tgt.getPower()*0.9));
    cities.save(tgt);

    // combat points: attacker population lost + enemy population killed
    awardCombat(m.getPlayerId(), popOf(r.attackerLost()) + popOf(r.defenderLost()));

    // hero: XP, wounds, armed-skill consumption, and the report snapshot
    HeroParticipation hp = resolveHero(attackerHero, defenderHero, r, outcome, now);

    // persist the battle report (both perspectives in one row)
    reports.createReport(m, new BattleResult(outcome,
        attackerSent, r.attackerLost(), r.attackerSurvived(),
        defenderPresent, r.defenderLost(), r.defenderSurvived(),
        resourcesStolen, r.attackerAttackPower(), r.defenderDefencePower(), r.siegeDamage()), hp);

    // the army (and any attacking hero) marches home unless wiped with no hero present
    if (r.attackerSurvived().isEmpty() && attackerHero == null) return;
    Movement ret = new Movement();
    ret.setWorldId(m.getWorldId()); ret.setPlayerId(m.getPlayerId()); ret.setSourceCityId(m.getSourceCityId());
    ret.setTargetCityId(m.getTargetCityId()); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(r.attackerSurvived()); ret.setLoot(loot);
    long secs = Math.max(5, now.getEpochSecond() - m.getDepartAt().getEpochSecond());
    ret.setArriveAt(now.plusSeconds(secs));
    Movement savedRet = movements.save(ret);
    if (attackerHero != null){ attackerHero.setActiveMovementId(savedRet.getId()); heroRepo.save(attackerHero); }
  }

  /** A node attack arrives: fight the node garrison, and on a win flip the node to CONTESTED. */
  private void resolveNodeAttack(Movement m, Instant now){
    ResourceNode node = resourceNodes.findById(m.getTargetNodeId()).orElse(null);
    if (node == null){ returnArmy(m, m.getUnits(), null); return; }
    nodeService.settle(node, now);

    Map<String,Integer> attackerSent = new LinkedHashMap<>(m.getUnits());
    Map<String,Integer> defenderPresent = new LinkedHashMap<>(node.getGarrison());

    Hero attackerHero = heroRepo.findByActiveMovementId(m.getId()).orElse(null);
    CombatEngine.Mods mods = attackerHero != null ? heroService.offenseMods(attackerHero) : CombatEngine.Mods.none();
    CombatEngine.Result r = combat.resolve(attackerSent, defenderPresent, mods);

    Long formerController = node.getControllingPlayerId();
    if (r.outcome() == BattleOutcome.VICTORY){
      // defender routed: garrison reduced to survivors, node opens up as CONTESTED
      node.setGarrison(new HashMap<>(r.defenderSurvived()));
      node.setStatus(NodeStatus.CONTESTED);
      node.setControllingPlayerId(null);
      node.setControllingAllianceId(null);
      node.setContestedUntil(now.plusSeconds(NodeService.CONTESTED_WINDOW_SECONDS));
    } else {
      // attack repelled: garrison takes its losses, node holds
      node.setGarrison(new HashMap<>(r.defenderSurvived()));
    }
    resourceNodes.save(node);

    awardCombat(m.getPlayerId(), popOf(r.attackerLost()) + popOf(r.defenderLost()));
    HeroParticipation hp = resolveHero(attackerHero, null, r, r.outcome(), now);
    reports.createNodeReport(m, new BattleResult(r.outcome(), attackerSent, r.attackerLost(), r.attackerSurvived(),
        defenderPresent, r.defenderLost(), r.defenderSurvived(),
        Map.of("WOOD",0L,"STONE",0L,"SILVER",0L), r.attackerAttackPower(), r.defenderDefencePower(), r.siegeDamage()),
        hp, nodeService.label(node), formerController);

    if (r.attackerSurvived().isEmpty() && attackerHero == null) return;
    Movement ret = new Movement();
    ret.setWorldId(m.getWorldId()); ret.setPlayerId(m.getPlayerId()); ret.setSourceCityId(m.getSourceCityId());
    ret.setTargetNodeId(m.getTargetNodeId()); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(r.attackerSurvived());
    ret.setArriveAt(now.plusSeconds(Math.max(5, now.getEpochSecond() - m.getDepartAt().getEpochSecond())));
    Movement savedRet = movements.save(ret);
    if (attackerHero != null){ attackerHero.setActiveMovementId(savedRet.getId()); heroRepo.save(attackerHero); }
  }

  /** Builds combined attacker/defender hero modifiers for the engine. */
  private CombatEngine.Mods combinedMods(Hero attackerHero, Hero defenderHero){
    CombatEngine.Mods atk = attackerHero != null ? heroService.offenseMods(attackerHero) : CombatEngine.Mods.none();
    CombatEngine.Mods def = defenderHero != null ? heroService.defenseMods(defenderHero) : CombatEngine.Mods.none();
    return new CombatEngine.Mods(atk.attackMult(), def.defenseMult(), def.sharpDefenseMult(), atk.attackerLossMult());
  }

  /** Applies XP / wounds / skill cooldowns to participating heroes and returns the report snapshot. */
  private HeroParticipation resolveHero(Hero attackerHero, Hero defenderHero, CombatEngine.Result r,
                                        BattleOutcome outcome, Instant now){
    // defender's stationed hero earns a little XP for holding the line
    if (defenderHero != null && outcome == BattleOutcome.DEFEAT){
      heroService.grantXp(defenderHero, Math.max(1, r.attackerAttackPower()/20));
      heroRepo.save(defenderHero);
    }
    if (attackerHero == null) return null;

    int bonusPct = heroService.attackBonusPct(attackerHero);
    int lossRedPct = heroService.lossReductionPct(attackerHero);
    String skillUsed = heroService.consumeArmedSkill(attackerHero, now);

    int xp = 0; Integer leveledTo = null;
    if (outcome == BattleOutcome.VICTORY){
      xp = Math.max(1, r.defenderDefencePower()/10);   // XP from defeated defence power
      leveledTo = heroService.grantXp(attackerHero, xp);
    }

    // wounded if the army was gutted (>70% of population sent was lost)
    int sentPop = popOf(r.attackerLost()) + popOf(r.attackerSurvived());
    boolean wounded = sentPop > 0 && (double) popOf(r.attackerLost()) / sentPop > HeroService.WOUND_THRESHOLD;
    if (wounded) heroService.wound(attackerHero, now);
    heroRepo.save(attackerHero);

    return new HeroParticipation(attackerHero.getName(), attackerHero.getLevel(),
        bonusPct, lossRedPct, skillUsed, xp, leveledTo, wounded);
  }

  private void resolveReturn(Movement m){
    returnArmy(m, m.getUnits(), m.getLoot());
    heroRepo.findByActiveMovementId(m.getId()).ifPresent(h -> heroService.arriveHome(h, m.getSourceCityId()));
  }

  private void returnArmy(Movement m, Map<String,Integer> army, Map<String,Long> loot){
    City home = cities.findById(m.getSourceCityId()).orElse(null); if (home==null) return;
    for (var e : army.entrySet()){
      String name = catalog.get(e.getKey()).getName();
      CityUnit cu = units.findByCityId(home.getId()).stream().filter(x->x.getType().equalsIgnoreCase(name)).findFirst()
        .orElseGet(()->new CityUnit(home.getId(), name, 0));
      cu.setCount(cu.getCount()+e.getValue()); units.save(cu);
    }
    if (loot != null){
      long cap = cityService.capacity(home.getId());
      home.setWood(Math.min(cap, home.getWood()+loot.getOrDefault("WOOD",0L)));
      home.setStone(Math.min(cap, home.getStone()+loot.getOrDefault("STONE",0L)));
      home.setSilver(Math.min(cap, home.getSilver()+loot.getOrDefault("SILVER",0L)));
      cities.save(home);
    }
  }

  private void awardCombat(Long playerId, int pts){
    if (playerId==null || pts<=0) return;
    Player p = players.findById(playerId).orElse(null); if (p==null) return;
    int cp = p.getCombatPoints()+pts, lvl = p.getLevel();
    while (cp >= GameRules.levelReq(lvl)){ cp -= GameRules.levelReq(lvl); lvl++; }
    p.setCombatPoints(cp); p.setLevel(lvl); players.save(p);
  }

  private long armyCarry(Map<String,Integer> a){ long s=0; for (var e:a.entrySet()) s+=(long)catalog.get(e.getKey()).getCarryCapacity()*e.getValue(); return s; }
  private int popOf(Map<String,Integer> a){ int s=0; for (var e:a.entrySet()) s+=catalog.get(e.getKey()).getPopulationCost()*e.getValue(); return s; }
}
