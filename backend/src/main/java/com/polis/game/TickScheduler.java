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
  private final TradeService trade;
  private final ProgressionService progression;
  private final AltarService altar;
  private final WonderService wonders;
  private final ColossusService colossi;
  private final SpyService spyService;
  private final ReinforcementRepo reinforcements;
  private final SiegeService siege;
  private final IslandBossService islandBoss;

  public TickScheduler(CityService cityService, CityFactory cityFactory, CityRepo cities, UnitRepo units,
                       JobRepo jobs, MovementRepo movements, PlayerRepo players, BattleReportService reports,
                       CombatEngine combat, UnitCatalog catalog, HeroService heroService, HeroRepo heroRepo,
                       NodeService nodeService, ResourceNodeRepo resourceNodes, SettleService settleService,
                       LibraryService library, TradeService trade,
                       ProgressionService progression, AltarService altar, WonderService wonders,
                       ColossusService colossi, SpyService spyService, ReinforcementRepo reinforcements,
                       SiegeService siege, IslandBossService islandBoss){
    this.wonders=wonders; this.colossi=colossi; this.spyService=spyService; this.reinforcements=reinforcements;
    this.siege=siege; this.islandBoss=islandBoss;
    this.cityService=cityService; this.cityFactory=cityFactory; this.cities=cities; this.units=units;
    this.jobs=jobs; this.movements=movements; this.players=players; this.reports=reports;
    this.combat=combat; this.catalog=catalog; this.heroService=heroService; this.heroRepo=heroRepo;
    this.nodeService=nodeService; this.resourceNodes=resourceNodes; this.settleService=settleService;
    this.library=library; this.trade=trade;
    this.progression=progression; this.altar=altar;
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
    // complete any due Altar festivals (grant Culture Points → level-ups)
    altar.completeDue(now);

    // resolve movements
    for (Movement m : movements.findDue(now)){
      // SETTLE parks on arrival awaiting the race choice — it is NOT marked resolved here.
      if (m.getPhase() == MovementPhase.SETTLE){ settleService.onArrive(m, now); continue; }
      switch (m.getPhase()){
        case COLONY -> resolveColony(m);
        case OUT     -> {
          if (m.getTargetColossusId()!=null) colossi.onArrive(m, now);
          else if (m.getTargetBossId()!=null) islandBoss.onArrive(m, now);   // resource-island boss strike
          else if (m.getTargetWonderId()!=null) wonders.resolveAttack(m, now);
          else if (m.getTargetNodeId()!=null) resolveNodeAttack(m, now);
          else if (m.isSiegeIntent()) siege.resolveSiegeStart(m, now);   // siege attempt → lay or fail the siege
          else resolveRaid(m, now);
        }
        case RETURN  -> resolveReturn(m);
        case SUPPORT -> resolveSupport(m);
        case SIEGE_REINFORCE -> siege.onReinforceArrive(m);              // join the besieging force
        case SIEGE_ATTACK    -> siege.onSiegeAttackArrive(m, now);       // try to break a lock
        case OCCUPY  -> { if (m.getTargetWonderId()!=null) wonders.resolveOccupy(m, now); else nodeService.resolveOccupy(m, now); }
        default      -> {}
      }
      m.setResolved(true); movements.save(m);
    }

    // foundings the player never completed: send the hero home, free the slot
    settleService.releaseAbandoned(now);

    // trade logistics: deliver arrived convoys, then dispatch queued ones into freed slots
    trade.deliverDue(now);
    trade.dispatchPending(now);

    // Colossus: time out any whose 1-hour window has elapsed (defeated ones already paid out)
    colossi.sweepDespawns(now);

    // resource-island bosses: respawn any whose cooldown has elapsed
    islandBoss.respawnDue(now);

    // resolve any due spy missions (the espionage contest)
    spyService.resolveDue(now);

    // sieges that survived their full duration → conquest
    siege.resolveDue(now);
  }

  /** Daily roaming Colossus: spawns at 21:00 server time (despawns at 22:00 via the tick sweep). */
  @Scheduled(cron = "${polis.colossus.spawn-cron:0 0 21 * * *}")
  public void spawnDailyColossus(){ colossi.spawnDaily(Instant.now()); }

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

    // defender garrison snapshot: the units stationed at the target right now, PLUS any friendly
    // reinforcements stationed here by allies (they fight on the host's behalf and take casualties).
    List<CityUnit> garrison = units.findByCityId(tgt.getId());
    List<Reinforcement> reinf = reinforcements.findByHostCityId(tgt.getId());
    Map<String,Integer> defenderPresent = new LinkedHashMap<>();
    for (CityUnit cu : garrison)
      if (cu.getCount() > 0) defenderPresent.merge(cu.getType().toUpperCase(), cu.getCount(), Integer::sum);
    for (Reinforcement r0 : reinf)
      if (r0.getUnits() != null) for (var e : r0.getUnits().entrySet())
        if (e.getValue() != null && e.getValue() > 0) defenderPresent.merge(e.getKey().toUpperCase(), e.getValue(), Integer::sum);

    // Two-layer combat: this dispatch is one layer — a SEA attack hits the enemy fleet, a LAND
    // attack the garrison. Resolve only the matching layer; the other is untouched. Transports
    // are cargo (never fight, never sunk in battle). A hero-only march fights on the land layer.
    CombatLayer layer = combat.attackLayer(attackerSent);
    if (layer == null) layer = CombatLayer.LAND;
    Map<String,Integer> attackerCombat = combat.combatants(attackerSent, layer);
    Map<String,Integer> defenderCombat = combat.combatants(defenderPresent, layer);

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

    // ── Library combat conditionals (Warpath / Bastion / Wild Hunt signature mechanics) ──
    // Bloodlust: a victorious attack streak within 6h stacks +3% attack (cap +15%).
    double bloodlust = atkLib.has("bloodlust") && srcCity != null
        ? 1 + Math.min(0.15, bloodlustStacks(srcCity, now) * 0.03) : 1.0;
    // Ambush: bonus attack vs a weaker target (lower city power).
    boolean ambush = atkLib.has("ambush") && srcCity != null && tgt.getPower() < srcCity.getPower();
    double ambushAtk = ambush ? 1.12 : 1.0;
    // Spiteful Walls: attackers assaulting this city suffer +15% extra losses.
    double spite = defLib.has("spitefulWalls") ? 1.15 : 1.0;
    // Last Bastion: a heavily-outnumbered garrison (4:1+) fights at +25% defense.
    int atkCount = countOf(attackerCombat), defCount = countOf(defenderCombat);
    double lastBastion = (defLib.has("lastBastion") && defCount > 0 && atkCount >= defCount * 4) ? 1.25 : 1.0;

    mods = new CombatEngine.Mods(
        mods.attackMult() * (atkRace!=null ? atkRace.attackMult : 1.0) * atkLib.attackMult() * bloodlust * ambushAtk,
        mods.defenseMult() * (tgt.getRace()!=null ? tgt.getRace().defenseMult : 1.0) * defLib.defenseMult() * lastBastion,
        mods.defFireMult()  * defLib.defFireMult(),
        mods.defWindMult()  * defLib.defWindMult(),
        mods.defEarthMult() * defLib.defEarthMult(),
        mods.defWaterMult() * defLib.defWaterMult(),
        mods.attackerLossMult() * spite);

    // per-unit Library upgrades (attacker buffs its units, defender buffs its garrison) + siege mult
    CombatEngine.UnitMods um = libUnitMods(atkLib, defLib, attackerHero);

    Element atkElement = atkRace != null ? atkRace.element : Element.FIRE;
    CombatEngine.CombatFx fx = attackerHero != null ? heroService.combatFx(attackerHero) : CombatEngine.CombatFx.none();
    double heroAtk = attackerHero != null ? heroService.baseAttack(attackerHero) : 0;
    CombatEngine.Result r = combat.resolve(attackerCombat, atkElement, defenderCombat, mods, fx, heroAtk, um);
    BattleOutcome outcome = r.outcome();

    // snapshot garrison counts so the retaliation heal credits only the garrison's own losses
    Map<Long,Integer> garrisonBefore = new HashMap<>();
    for (CityUnit cu : garrison) garrisonBefore.put(cu.getId(), cu.getCount());

    // apply defender casualties across the garrison AND any stationed reinforcements (proportional)
    applyDefenderLosses(garrison, reinf, r.defenderLost());

    // RETALIATION_HEAL: a defending hero recovers a fraction of the GARRISON's own losses on a hold
    if (outcome == BattleOutcome.DEFEAT && defenderHero != null){
      double heal = heroService.retaliationHealPct(defenderHero);
      if (heal > 0) for (CityUnit cu : garrison){
        int lostHere = Math.max(0, garrisonBefore.getOrDefault(cu.getId(), cu.getCount()) - cu.getCount());
        int back = (int)Math.round(lostHere * heal);
        if (back > 0){ cu.setCount(cu.getCount()+back); units.save(cu); }
      }
    }

    // plunder: victory only — surviving attackers carry off resources up to their carry capacity.
    // Base resources are lootable from anyone; a special resource only from a city of its race.
    Map<String,Long> resourcesStolen = new LinkedHashMap<>();
    resourcesStolen.put("WOOD",0L); resourcesStolen.put("STONE",0L); resourcesStolen.put("WHEAT",0L);
    Map<String,Long> loot = null;
    // Only a LAND victory plunders the city — a SEA victory just clears the harbor fleet.
    if (outcome == BattleOutcome.VICTORY && layer == CombatLayer.LAND){
      long carry = armyCarry(r.attackerSurvived());
      if (atkRace != null) carry = (long)(carry * atkRace.lootMult);                    // race loot bonus
      carry = (long)(carry * atkLib.lootMult());                                        // Library carry-capacity research (Plunderer's Haul)
      carry = (long)(carry * atkLib.lootStolenMult());                                  // Pillage / Plunderer's Haul steal bonus
      if (ambush) carry = (long)(carry * 1.15);                                         // Ambush: +15% loot vs weaker target
      if (attackerHero != null) carry = (long)(carry * heroService.lootMult(attackerHero)); // Cunning
      // lootable pool: the three base resources + the target's OWN race special (race-locked source)
      List<ResourceType> lootable = new ArrayList<>(List.of(ResourceType.WOOD, ResourceType.STONE, ResourceType.WHEAT));
      if (tgt.getRace() != null) lootable.add(tgt.getRace().specialResource);
      long pool = 0; for (ResourceType rt : lootable) pool += (long) tgt.get(rt);
      pool = (long)(pool * (1 - defLib.lootProtectFrac()));                             // Hidden Granaries: protect a slice of the hoard
      long want = Math.min(carry, pool);
      if (want > 0 && pool > 0){
        for (ResourceType rt : lootable){
          long taken = (long)(want * tgt.get(rt) / pool);
          if (taken <= 0) continue;
          tgt.add(rt, -taken);
          resourcesStolen.merge(rt.name(), taken, Long::sum);
        }
        loot = new HashMap<>(resourcesStolen);
      }
    }
    // power still tracks the beating a city has taken (drives barbarian difficulty/score)
    tgt.setPower(outcome == BattleOutcome.VICTORY ? Math.max(40, tgt.getPower()*0.45+20) : Math.max(40, tgt.getPower()*0.9));
    cities.save(tgt);

    // Bloodlust: keep the attacker's victory streak (or break it) after the fight is decided.
    if (srcCity != null && atkLib.has("bloodlust")){ updateBloodlust(srcCity, outcome, now); cities.save(srcCity); }

    // Combat Points (spendable war currency / festival fuel) — anti-farmed; the winner earns them.
    Player defP = tgt.getPlayerId()!=null ? players.findById(tgt.getPlayerId()).orElse(null) : null;
    Player atkP = m.getPlayerId()!=null ? players.findById(m.getPlayerId()).orElse(null) : null;
    ProgressionService.CombatAward award = outcome == BattleOutcome.VICTORY
        ? progression.combatAward(m.getPlayerId(), defP, countOf(r.defenderLost()), r.attackerAttackPower(), r.defenderDefencePower())
        : progression.combatAward(tgt.getPlayerId(), atkP, countOf(r.attackerLost()), r.defenderDefencePower(), r.attackerAttackPower());
    Long winnerId = outcome == BattleOutcome.VICTORY ? m.getPlayerId() : tgt.getPlayerId();
    if (award.points() > 0 && winnerId != null){
      Player w = players.findById(winnerId).orElse(null);
      if (w != null){
        // available balance (spendable on festivals) AND lifetime total (rankings) both rise on a win
        w.setCombatPoints(w.getCombatPoints() + award.points());
        w.setCombatPointsTotal(w.getCombatPointsTotal() + award.points());
        players.save(w);
      }
    }

    // hero: XP, wounds, armed-skill consumption, and the report snapshot
    HeroParticipation hp = resolveHero(attackerHero, defenderHero, r, outcome, now);

    // persist the battle report (both perspectives in one row) — only the engaged layer's units
    reports.createReport(m, new BattleResult(outcome,
        attackerCombat, r.attackerLost(), r.attackerSurvived(),
        defenderCombat, r.defenderLost(), r.defenderSurvived(),
        resourcesStolen, r.attackerAttackPower(), r.defenderDefencePower(), r.siegeDamage(),
        r.attackByElement(), r.defenseByElement()), hp, layer, award.points(), award.reason());

    // surviving combatants + any cargo (transports never fight) march home
    Map<String,Integer> returnUnits = new LinkedHashMap<>(r.attackerSurvived());
    for (var e : attackerSent.entrySet())
      if (!attackerCombat.containsKey(e.getKey())) returnUnits.merge(e.getKey(), e.getValue(), Integer::sum);
    // the army (and any attacking hero) marches home unless wiped with no hero present
    if (returnUnits.isEmpty() && attackerHero == null) return;
    Movement ret = new Movement();
    ret.setWorldId(m.getWorldId()); ret.setPlayerId(m.getPlayerId()); ret.setSourceCityId(m.getSourceCityId());
    ret.setTargetCityId(m.getTargetCityId()); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(returnUnits); ret.setLoot(loot);
    long secs = Math.max(5, now.getEpochSecond() - m.getDepartAt().getEpochSecond());
    ret.setArriveAt(now.plusSeconds(secs));
    Movement savedRet = movements.save(ret);
    if (attackerHero != null){ attackerHero.setActiveMovementId(savedRet.getId()); heroRepo.save(attackerHero); }
  }

  /** An enemy strike on a resource building arrives: fight the whole garrison; on a win the attacker
   *  SEIZES it (their survivors become the new garrison, their alliance takes control). */
  private void resolveNodeAttack(Movement m, Instant now){
    ResourceNode node = resourceNodes.findById(m.getTargetNodeId()).orElse(null);
    if (node == null){ returnArmy(m, m.getUnits(), null); return; }

    Map<String,Integer> attackerSent = new LinkedHashMap<>(m.getUnits());
    Map<String,Integer> defenderPresent = nodeService.flatGarrisonPublic(node);

    Hero attackerHero = heroRepo.findByActiveMovementId(m.getId()).orElse(null);
    CombatEngine.Mods mods = attackerHero != null ? heroService.offenseMods(attackerHero) : CombatEngine.Mods.none();
    CombatEngine.CombatFx fx = attackerHero != null ? heroService.combatFx(attackerHero) : CombatEngine.CombatFx.none();
    City nSrc = cities.findById(m.getSourceCityId()).orElse(null);
    Element nElement = nSrc!=null && nSrc.getRace()!=null ? nSrc.getRace().element : Element.FIRE;
    double nHeroAtk = attackerHero != null ? heroService.baseAttack(attackerHero) : 0;
    CombatEngine.Result r = combat.resolve(attackerSent, nElement, defenderPresent, mods, fx, nHeroAtk);

    Long formerController = node.getControllingPlayerId();
    boolean won = r.outcome() == BattleOutcome.VICTORY;
    nodeService.applyAttackResult(node, m.getPlayerId(),
        new LinkedHashMap<>(r.attackerSurvived()), new LinkedHashMap<>(r.defenderLost()), won, now);

    HeroParticipation hp = resolveHero(attackerHero, null, r, r.outcome(), now);
    reports.createNodeReport(m, new BattleResult(r.outcome(), attackerSent, r.attackerLost(), r.attackerSurvived(),
        defenderPresent, r.defenderLost(), r.defenderSurvived(),
        Map.of("WOOD",0L,"STONE",0L,"WHEAT",0L), r.attackerAttackPower(), r.defenderDefencePower(), r.siegeDamage(),
        r.attackByElement(), r.defenseByElement()),
        hp, nodeService.label(node), formerController);

    // On a WIN the attacker's survivors GARRISON the seized building (they stay). On a loss, survivors march home.
    if (won){
      if (attackerHero != null) heroService.arriveHome(attackerHero, m.getSourceCityId());
      return;
    }
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
    return new CombatEngine.Mods(atk.attackMult(), def.defenseMult(),
        def.defFireMult(), def.defWindMult(), def.defEarthMult(), def.defWaterMult(), atk.attackerLossMult());
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

  /** Reinforcements arrive: station them at the host city (merge into its support pool). */
  private void resolveSupport(Movement m){
    City host = cities.findById(m.getTargetCityId()).orElse(null);
    if (host == null){ returnArmy(m, m.getUnits(), null); return; }   // target vanished → march the troops home
    // key by origin city too, so each contributing city's troops return to where they came from
    Reinforcement r = reinforcements.findByHostCityIdAndOwnerPlayerIdAndOriginCityId(host.getId(), m.getPlayerId(), m.getSourceCityId())
        .orElseGet(() -> { Reinforcement n = new Reinforcement();
          n.setWorldId(m.getWorldId()); n.setHostCityId(host.getId()); n.setOwnerPlayerId(m.getPlayerId());
          n.setOriginCityId(m.getSourceCityId());
          return n; });
    Map<String,Integer> u = r.getUnits()==null ? new LinkedHashMap<>() : new LinkedHashMap<>(r.getUnits());
    for (var e : m.getUnits().entrySet())
      if (e.getValue()!=null && e.getValue()>0) u.merge(e.getKey().toUpperCase(), e.getValue(), Integer::sum);
    r.setUnits(u);
    reinforcements.save(r);
  }

  /**
   * Distribute the engine's defender losses (by UPPERCASE unit type) across the garrison stacks and
   * any stationed reinforcement stacks, proportional to each stack's count of that type. Persists the
   * survivors and deletes emptied reinforcement records.
   */
  private void applyDefenderLosses(List<CityUnit> garrison, List<Reinforcement> reinf, Map<String,Integer> lost){
    for (var e : lost.entrySet()){
      String type = e.getKey(); int remaining = e.getValue() == null ? 0 : e.getValue();
      if (remaining <= 0) continue;
      // total of this type present across garrison + reinforcements
      int total = 0;
      for (CityUnit cu : garrison) if (cu.getType().equalsIgnoreCase(type)) total += cu.getCount();
      for (Reinforcement r : reinf) total += r.getUnits().getOrDefault(type, 0);
      if (total <= 0) continue;
      // proportional removal; the garrison absorbs any rounding remainder last
      for (Reinforcement r : reinf){
        int have = r.getUnits().getOrDefault(type, 0);
        if (have <= 0) continue;
        int take = Math.min(have, (int)Math.floor((double)e.getValue() * have / total));
        if (take > 0){ r.getUnits().put(type, have - take); remaining -= take; }
      }
      for (CityUnit cu : garrison){
        if (remaining <= 0) break;
        if (!cu.getType().equalsIgnoreCase(type)) continue;
        int take = Math.min(cu.getCount(), remaining);
        cu.setCount(cu.getCount() - take); remaining -= take;
      }
      // any leftover (rounding) falls on remaining reinforcement stacks
      for (Reinforcement r : reinf){
        if (remaining <= 0) break;
        int have = r.getUnits().getOrDefault(type, 0);
        int take = Math.min(have, remaining);
        if (take > 0){ r.getUnits().put(type, have - take); remaining -= take; }
      }
    }
    for (CityUnit cu : garrison) units.save(cu);
    for (Reinforcement r : reinf){
      r.getUnits().values().removeIf(v -> v == null || v <= 0);
      if (r.getUnits().isEmpty()) reinforcements.delete(r);
      else reinforcements.save(r);
    }
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
      for (var e : loot.entrySet()){
        if (e.getValue()==null || e.getValue()<=0) continue;
        ResourceType rt; try { rt = ResourceType.valueOf(e.getKey()); } catch (Exception ex){ continue; }
        home.set(rt, Math.min(cap, home.get(rt)+e.getValue()));
      }
      cities.save(home);
    }
  }


  /** Effective Bloodlust stacks: 0 if the 6h streak window has lapsed, else the stored count. */
  private int bloodlustStacks(City c, Instant now){
    Instant last = c.getBloodlustLastWin();
    if (last == null || last.plusSeconds(6*3600).isBefore(now)) return 0;
    return c.getBloodlustStacks();
  }
  /** A win extends the streak (cap 5 = +15%); any loss breaks it. */
  private void updateBloodlust(City c, BattleOutcome outcome, Instant now){
    if (outcome == BattleOutcome.VICTORY){
      c.setBloodlustStacks(Math.min(5, bloodlustStacks(c, now) + 1));
      c.setBloodlustLastWin(now);
    } else { c.setBloodlustStacks(0); c.setBloodlustLastWin(null); }
  }
  /** Per-unit Library upgrades + siege multiplier for a raid (attacker buffs, defender buffs, siege). */
  private CombatEngine.UnitMods libUnitMods(LibraryService.LibEffects atkLib, LibraryService.LibEffects defLib, Hero attackerHero){
    Map<String,Double> atk = new HashMap<>(), def = new HashMap<>();
    if (atkLib.has("honedRaiders"))   atk.put("RAIDER",   1.15);  // Honed Raiders: +15% attack
    if (atkLib.has("packInstincts"))  atk.put("OUTRIDER", 1.12);  // Pack Instincts: +12% attack (speed handled in movement)
    if (defLib.has("temperedWardens"))def.put("WARDEN",   1.20);  // Tempered Wardens: +20% defense
    if (defLib.has("honedRaiders"))   def.put("RAIDER",   1.10);  // Honed Raiders: +10% HP ≈ +10% defense
    double siege = atkLib.siegeWallMult();                        // Breach Engines: +20% wall damage
    if (attackerHero != null && atkLib.has("siegebreaker")) siege *= 1.40;  // Siegebreaker hero: +40%
    return new CombatEngine.UnitMods(atk, def, siege);
  }

  private long armyCarry(Map<String,Integer> a){ long s=0; for (var e:a.entrySet()) s+=(long)catalog.get(e.getKey()).getCarryCapacity()*e.getValue(); return s; }
  private int popOf(Map<String,Integer> a){ int s=0; for (var e:a.entrySet()) s+=catalog.get(e.getKey()).getPopulationCost()*e.getValue(); return s; }
  /** Total troop COUNT in a unit map (Combat Points are earned per enemy troop killed). */
  private int countOf(Map<String,Integer> a){ int s=0; for (var v:a.values()) if (v!=null) s+=v; return s; }
}
