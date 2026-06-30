package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Siege &amp; Conquest. A siege is laid by a winning hero-led attack that brings ≥1 Defense ship
 * (validated at dispatch in {@link BuildService}); the surviving land troops + ships + hero become
 * the besieging force. It holds for a FIXED duration; reinforcement never resets the clock. Two
 * independent break locks — all besieging troops destroyed (LAND) OR all besieging ships destroyed
 * (SEA) — end it immediately. Survive to {@code endsAt} and the city is conquered.
 *
 * <p>Combat reuses {@link CombatEngine} (two-layer, elemental). Siege battles fold in race +
 * Library defense/attack multipliers and the leading hero; the per-unit Library upgrades and the
 * Bloodlust/Ambush streak mechanics are intentionally omitted for siege battles (kept simple).
 * The besieging hero counts toward the siege's defense in every break attempt.
 */
@Service
public class SiegeService {
  private final SiegeRepo sieges;
  private final CityRepo cities;
  private final UnitRepo units;
  private final ReinforcementRepo reinforcements;
  private final HeroRepo heroRepo;
  private final PlayerRepo players;
  private final MovementRepo movements;
  private final CombatEngine combat;
  private final UnitCatalog catalog;
  private final HeroService heroService;
  private final LibraryService library;
  private final BattleReportService reports;
  private final TravelTimeService travel;
  private final BuildService build;
  private final MessageRepo messages;

  @Value("${polis.siege.hours:8}") private int siegeHours;

  public SiegeService(SiegeRepo sieges, CityRepo cities, UnitRepo units, ReinforcementRepo reinforcements,
                      HeroRepo heroRepo, PlayerRepo players, MovementRepo movements, CombatEngine combat,
                      UnitCatalog catalog, HeroService heroService, LibraryService library,
                      BattleReportService reports, TravelTimeService travel, BuildService build, MessageRepo messages){
    this.sieges=sieges; this.cities=cities; this.units=units; this.reinforcements=reinforcements;
    this.heroRepo=heroRepo; this.players=players; this.movements=movements; this.combat=combat;
    this.catalog=catalog; this.heroService=heroService; this.library=library; this.reports=reports;
    this.travel=travel; this.build=build; this.messages=messages;
  }

  // ════════════════════════════ START (from the tick, OUT + siege intent) ════════════════════════

  /**
   * A siege-intent attack arrives. Resolve the LAND assault (troops + hero vs garrison) and the SEA
   * blockade (ships vs fleet) separately. The siege begins iff the LAND assault wins AND at least one
   * combatant ship survives; otherwise it's a normal failed assault and the survivors march home.
   * Returns true if the movement was fully handled here (so the scheduler can skip its raid path).
   */
  @Transactional
  public void resolveSiegeStart(Movement m, Instant now){
    City tgt = cities.findById(m.getTargetCityId()).orElse(null);
    Hero hero = heroRepo.findByActiveMovementId(m.getId()).orElse(null);
    if (tgt == null){ sendReturn(m.getWorldId(), m.getSourceCityId(), m.getTargetCityId(), m.getUnits(), hero, now); return; }

    Map<String,Integer> sent = new LinkedHashMap<>(m.getUnits());
    // defender snapshot: garrison + stationed reinforcements
    List<CityUnit> garrison = units.findByCityId(tgt.getId());
    List<Reinforcement> reinf = reinforcements.findByHostCityId(tgt.getId());
    Map<String,Integer> defenderPresent = mergeDefenders(garrison, reinf);

    Map<String,Integer> landAtk = combat.combatants(sent, CombatLayer.LAND);
    Map<String,Integer> seaAtk  = combat.combatants(sent, CombatLayer.SEA);
    Map<String,Integer> defLand = combat.combatants(defenderPresent, CombatLayer.LAND);
    Map<String,Integer> defSea  = combat.combatants(defenderPresent, CombatLayer.SEA);

    Hero defHero = tgt.getPlayerId()==null ? null :
        heroRepo.findByStationedCityIdAndStateAndUnlockedTrue(tgt.getId(), HeroState.IDLE).stream().findFirst().orElse(null);
    City src = cities.findById(m.getSourceCityId()).orElse(null);
    Race atkRace = src!=null ? src.getRace() : null;
    Element atkElement = atkRace!=null ? atkRace.element : Element.FIRE;
    LibraryService.LibEffects atkLib = src!=null ? library.effects(src.getId()) : LibraryService.LibEffects.none();
    LibraryService.LibEffects defLib = library.effects(tgt.getId());

    CombatEngine.Mods landMods = battleMods(atkRace, atkLib, hero, tgt.getRace(), defLib, defHero);
    CombatEngine.Mods seaMods  = battleMods(atkRace, atkLib, null, tgt.getRace(), defLib, defHero);
    CombatEngine.CombatFx fx = hero!=null ? heroService.combatFx(hero) : CombatEngine.CombatFx.none();
    double heroAtk = hero!=null ? heroService.baseAttack(hero) : 0;

    CombatEngine.Result land = combat.resolve(landAtk, atkElement, defLand, landMods, fx, heroAtk);
    CombatEngine.Result sea  = combat.resolve(seaAtk,  atkElement, defSea,  seaMods);

    // defender casualties from BOTH layers fall on the garrison + reinforcements
    Map<String,Integer> defLost = new LinkedHashMap<>(land.defenderLost());
    sea.defenderLost().forEach((k,v) -> defLost.merge(k, v, Integer::sum));
    applyDefenderLosses(garrison, reinf, defLost);

    Map<String,Integer> landSurv = new LinkedHashMap<>(land.attackerSurvived());
    // sea survivors: combatant ships + any transports that rode along (transports never fight)
    Map<String,Integer> seaSurv = new LinkedHashMap<>(sea.attackerSurvived());
    for (var e : sent.entrySet()){
      UnitType u = catalog.get(e.getKey());
      if (u.isSea() && u.getShipRole()==ShipRole.TRANSPORT) seaSurv.merge(e.getKey(), e.getValue(), Integer::sum);
    }
    int shipsLeft = countCombatants(seaSurv, CombatLayer.SEA);
    boolean siegeBegins = land.outcome()==BattleOutcome.VICTORY && shipsLeft > 0;

    // report the assault (land layer is the headline)
    reports.createReport(m, new BattleResult(land.outcome(),
        landAtk, land.attackerLost(), land.attackerSurvived(),
        defLand, land.defenderLost(), land.defenderSurvived(),
        emptyLoot(), land.attackerAttackPower(), land.defenderDefencePower(), land.siegeDamage(),
        land.attackByElement(), land.defenseByElement()), null, CombatLayer.LAND, 0, null);

    if (!siegeBegins){
      // failed assault: survivors of both layers (and the hero) march home; no plunder
      Map<String,Integer> home = new LinkedHashMap<>(landSurv);
      seaSurv.forEach((k,v)->home.merge(k,v,Integer::sum));
      if (hero != null && countWounds(land) ) heroService.wound(hero, now);
      sendReturn(m.getWorldId(), m.getSourceCityId(), m.getTargetCityId(), home, hero, now);
      return;
    }

    // SIEGE BEGINS — lock the force and the hero onto the city
    Player atkP = players.findById(m.getPlayerId()).orElse(null);
    Siege s = new Siege();
    s.setWorldId(m.getWorldId());
    s.setCityId(tgt.getId());
    s.setBesiegingPlayerId(m.getPlayerId());
    s.setBesiegingAllianceId(atkP!=null ? atkP.getAllianceId() : null);
    s.setOriginCityId(m.getSourceCityId());
    s.setStatus(SiegeStatus.ACTIVE);
    s.setStartedAt(now);
    s.setEndsAt(now.plusSeconds(GameRules.fast((long)siegeHours*3600)));   // TIME_SCALE for test
    s.setBesiegingTroops(new LinkedHashMap<>(landSurv));
    s.setBesiegingShips(new LinkedHashMap<>(seaSurv));
    if (hero != null){
      s.setHeroId(hero.getId());
      hero.setState(HeroState.BESIEGING);
      hero.setStationedCityId(tgt.getId());
      hero.setActiveMovementId(null);
      heroRepo.save(hero);
    }
    sieges.save(s);

    tgt.setPower(Math.max(40, tgt.getPower()*0.45+20));
    cities.save(tgt);

    notify(m.getPlayerId(), tgt.getPlayerId(),
        "The siege of " + tgt.getName() + " has begun — hold it for " + siegeHours + "h to conquer the city.",
        "Your city " + tgt.getName() + " is under SIEGE! Break it within " + siegeHours + "h or it falls.");
  }

  // ════════════════════════════ REINFORCE (attacker + allies) ════════════════════════════════════

  @Transactional
  public Movement reinforce(Long playerId, Long siegeId, Long fromCityId, Map<String,Integer> army){
    Siege s = active(siegeId);
    if (!canBesiege(playerId, s)) throw new IllegalStateException("Only the besieger or their alliance can reinforce this siege");
    if (army==null || army.isEmpty()) throw new IllegalArgumentException("Select at least one unit to send");
    City src = owned(playerId, fromCityId);
    City city = cities.findById(s.getCityId()).orElseThrow();
    combat.attackLayer(army);                              // one layer per dispatch (transports may ride either)
    travel.requireTransport(army, src.getIslandId(), city.getIslandId(), 0);
    build.deductGarrison(fromCityId, army);
    long secs = travelSecs(src, fromCityId, s.getCityId(), army);
    Movement m = new Movement();
    m.setWorldId(src.getWorldId()); m.setPlayerId(playerId); m.setSourceCityId(fromCityId);
    m.setTargetCityId(s.getCityId()); m.setTargetSiegeId(siegeId); m.setPhase(MovementPhase.SIEGE_REINFORCE);
    m.setUnits(new HashMap<>(army)); m.setArriveAt(Instant.now().plusSeconds(Math.max(5, secs)));
    return movements.save(m);
  }

  /** Reinforcements reach the siege: add to the besieging force by layer. Never resets the clock. */
  @Transactional
  public void onReinforceArrive(Movement m){
    Siege s = sieges.findById(m.getTargetSiegeId()).orElse(null);
    if (s == null || s.getStatus()!=SiegeStatus.ACTIVE){    // siege ended mid-march → the troops go home
      Hero none = null; sendReturn(m.getWorldId(), m.getSourceCityId(), m.getTargetCityId(), m.getUnits(), none, Instant.now());
      return;
    }
    Map<String,Integer> troops = s.getBesiegingTroops()==null ? new LinkedHashMap<>() : new LinkedHashMap<>(s.getBesiegingTroops());
    Map<String,Integer> ships  = s.getBesiegingShips()==null  ? new LinkedHashMap<>() : new LinkedHashMap<>(s.getBesiegingShips());
    for (var e : m.getUnits().entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      String k = e.getKey().toUpperCase();
      if (catalog.get(k).isSea()) ships.merge(k, e.getValue(), Integer::sum);
      else                         troops.merge(k, e.getValue(), Integer::sum);
    }
    s.setBesiegingTroops(troops); s.setBesiegingShips(ships);
    sieges.save(s);
  }

  // ════════════════════════════ BREAK ATTEMPT (defender + allies) ═════════════════════════════════

  @Transactional
  public Movement attackSiege(Long playerId, Long siegeId, Long fromCityId, Map<String,Integer> army, Long heroId){
    Siege s = active(siegeId);
    City city = cities.findById(s.getCityId()).orElseThrow();
    if (!canDefend(playerId, city)) throw new IllegalStateException("Only the city owner or their alliance can break this siege");
    if ((army==null || army.isEmpty()) && heroId==null) throw new IllegalArgumentException("Select at least one unit or a hero");
    City src = owned(playerId, fromCityId);
    combat.attackLayer(army);                              // one layer per break attempt (LAND vs troops, SEA vs ships)
    Hero hero = heroId != null ? heroService.requireOwned(playerId, heroId) : null;
    int heroLoad = hero != null ? travel.heroLandLoad(hero.getRace()) : 0;
    travel.requireTransport(army, src.getIslandId(), city.getIslandId(), heroLoad);
    build.deductGarrison(fromCityId, army);
    long secs = travelSecs(src, fromCityId, s.getCityId(), army);
    Movement m = new Movement();
    m.setWorldId(src.getWorldId()); m.setPlayerId(playerId); m.setSourceCityId(fromCityId);
    m.setTargetCityId(s.getCityId()); m.setTargetSiegeId(siegeId); m.setPhase(MovementPhase.SIEGE_ATTACK);
    m.setUnits(new HashMap<>(army)); m.setArriveAt(Instant.now().plusSeconds(Math.max(5, secs)));
    Movement saved = movements.save(m);
    if (heroId != null) heroService.sendHero(playerId, heroId, fromCityId, saved.getId());
    return saved;
  }

  /** A break force reaches the siege: fight the besieging force on the matching layer; check the locks. */
  @Transactional
  public void onSiegeAttackArrive(Movement m, Instant now){
    Siege s = sieges.findById(m.getTargetSiegeId()).orElse(null);
    Hero atkHero = heroRepo.findByActiveMovementId(m.getId()).orElse(null);
    if (s == null || s.getStatus()!=SiegeStatus.ACTIVE){   // nothing to break → the force returns home
      sendReturn(m.getWorldId(), m.getSourceCityId(), m.getTargetCityId(), m.getUnits(), atkHero, now);
      return;
    }
    CombatLayer layer = combat.attackLayer(m.getUnits());
    if (layer == null) layer = CombatLayer.LAND;
    Map<String,Integer> atk = combat.combatants(m.getUnits(), layer);
    Map<String,Integer> defMap = layer==CombatLayer.SEA ? s.getBesiegingShips() : s.getBesiegingTroops();
    Map<String,Integer> defenders = combat.combatants(defMap==null?Map.of():defMap, layer);

    City src = cities.findById(m.getSourceCityId()).orElse(null);
    Race atkRace = src!=null ? src.getRace() : null;
    Element atkElement = atkRace!=null ? atkRace.element : Element.FIRE;
    LibraryService.LibEffects atkLib = src!=null ? library.effects(src.getId()) : LibraryService.LibEffects.none();
    Hero siegeHero = s.getHeroId()!=null ? heroRepo.findById(s.getHeroId()).orElse(null) : null;

    // besieging force defends; the locked siege hero counts toward its defense on EITHER layer
    CombatEngine.Mods mods = battleMods(atkRace, atkLib, atkHero, null, LibraryService.LibEffects.none(), siegeHero);
    CombatEngine.CombatFx fx = atkHero!=null ? heroService.combatFx(atkHero) : CombatEngine.CombatFx.none();
    double heroAtk = atkHero!=null ? heroService.baseAttack(atkHero) : 0;
    CombatEngine.Result r = combat.resolve(atk, atkElement, defenders, mods, fx, heroAtk);

    // apply the siege force's losses to the matching map
    Map<String,Integer> updated = new LinkedHashMap<>(defMap==null?Map.of():defMap);
    for (var e : r.defenderLost().entrySet()){
      int have = updated.getOrDefault(e.getKey(), 0);
      int left = Math.max(0, have - (e.getValue()==null?0:e.getValue()));
      if (left>0) updated.put(e.getKey(), left); else updated.remove(e.getKey());
    }
    if (layer==CombatLayer.SEA) s.setBesiegingShips(updated); else s.setBesiegingTroops(updated);

    // report (city owner is the "attacker" of the siege camp here, perspective preserved via movement)
    reports.createReport(m, new BattleResult(r.outcome(),
        atk, r.attackerLost(), r.attackerSurvived(),
        combat.combatants(defMap==null?Map.of():defMap, layer), r.defenderLost(), r.defenderSurvived(),
        emptyLoot(), r.attackerAttackPower(), r.defenderDefencePower(), r.siegeDamage(),
        r.attackByElement(), r.defenseByElement()), null, layer, 0, null);

    // break-force survivors march home (with their hero); the hero gains XP / may be wounded
    if (atkHero != null){
      if (r.outcome()==BattleOutcome.VICTORY) heroService.grantXp(atkHero, Math.max(1, r.defenderDefencePower()/10));
      if (countWounds(r)) heroService.wound(atkHero, now);
      heroRepo.save(atkHero);
    }
    sendReturn(m.getWorldId(), m.getSourceCityId(), m.getTargetCityId(), r.attackerSurvived(), atkHero, now);

    // CHECK THE TWO LOCKS — either at zero ends the siege immediately
    boolean landGone = countCombatants(s.getBesiegingTroops(), CombatLayer.LAND) == 0;
    boolean seaGone  = countCombatants(s.getBesiegingShips(),  CombatLayer.SEA)  == 0;
    if (landGone || seaGone) breakSiege(s, now, landGone);
    else sieges.save(s);
  }

  // ════════════════════════════ RESOLUTION (scheduled) ════════════════════════════════════════════

  /** Tick sweep: any ACTIVE siege past its end time → CONQUEST. */
  @Transactional
  public void resolveDue(Instant now){
    for (Siege s : sieges.findByStatusAndEndsAtLessThanEqual(SiegeStatus.ACTIVE, now)) conquer(s, now);
  }

  private void conquer(Siege s, Instant now){
    City city = cities.findById(s.getCityId()).orElse(null);
    if (city == null){ s.setStatus(SiegeStatus.SUCCEEDED); sieges.save(s); freeHeroIdle(s, null); return; }
    Long oldOwner = city.getPlayerId();

    // transfer ownership; keep buildings/levels/race intact, but the new owner must pick a race.
    // A conquered city is never the new owner's capital (they keep their own) — clear the flag so a
    // player can't end up with two capitals (which breaks the unique-capital lookups at startup).
    city.setPlayerId(s.getBesiegingPlayerId());
    city.setConqueredPendingRace(true);
    city.setCapital(false);
    cities.save(city);

    // the city's old garrison falls; allied reinforcements scatter
    for (CityUnit cu : units.findByCityId(city.getId())){ cu.setCount(0); units.save(cu); }
    for (Reinforcement r : reinforcements.findByHostCityId(city.getId())) reinforcements.delete(r);

    // the surviving besiegers become the new garrison
    Map<String,Integer> garrison = new LinkedHashMap<>();
    if (s.getBesiegingTroops()!=null) s.getBesiegingTroops().forEach((k,v)->garrison.merge(k,v,Integer::sum));
    if (s.getBesiegingShips()!=null)  s.getBesiegingShips().forEach((k,v)->garrison.merge(k,v,Integer::sum));
    for (var e : garrison.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      String name = catalog.get(e.getKey()).getName();
      CityUnit cu = units.findByCityId(city.getId()).stream().filter(x->x.getType().equalsIgnoreCase(name)).findFirst()
          .orElseGet(()->new CityUnit(city.getId(), name, 0));
      cu.setCount(cu.getCount()+e.getValue()); units.save(cu);
    }

    // the besieging hero is freed and marches back to its OWN city — it is NOT auto-assigned to the
    // conquered city (a city holds one hero, chosen by the player). The new city starts hero-less.
    String heroName = null;
    if (s.getHeroId()!=null){
      Hero h = heroRepo.findById(s.getHeroId()).orElse(null);
      s.setHeroId(null);
      if (h != null){ heroName = h.getName(); sendReturn(s.getWorldId(), s.getOriginCityId(), city.getId(), Map.of(), h, now); }
    }
    s.setStatus(SiegeStatus.SUCCEEDED); sieges.save(s);

    String ledBy = heroName != null ? " — your hero " + heroName + " led the conquest" : "";
    notify(s.getBesiegingPlayerId(), oldOwner,
        "Conquest! " + city.getName() + " has fallen and is now yours" + ledBy
            + ". Choose its race to complete the takeover.",
        "Your city " + city.getName() + " was conquered after an 8h siege.");
  }

  /** A break lock hit zero: end the siege. Survivors REMAIN stationed (status BROKEN) until withdrawn. */
  private void breakSiege(Siege s, Instant now, boolean landWiped){
    s.setStatus(SiegeStatus.BROKEN);
    Hero hero = s.getHeroId()!=null ? heroRepo.findById(s.getHeroId()).orElse(null) : null;
    if (hero != null){
      if (landWiped){                                  // the camp (where the hero stands) was overrun
        heroService.wound(hero, now);
        hero.setStationedCityId(s.getOriginCityId());  // recovers back home
        heroRepo.save(hero);
      } else {                                          // sea lock: hero unharmed, marches home
        s.setHeroId(null); sieges.save(s);
        sendReturn(s.getWorldId(), s.getOriginCityId(), s.getCityId(), Map.of(), hero, now);
      }
    }
    sieges.save(s);
    City city = cities.findById(s.getCityId()).orElse(null);
    notify(s.getBesiegingPlayerId(), city!=null?city.getPlayerId():null,
        "Your siege of " + (city!=null?city.getName():"the city") + " was broken. Surviving troops remain — withdraw them when ready.",
        "You broke the siege on " + (city!=null?city.getName():"your city") + "! Enemy survivors still occupy it until withdrawn.");
  }

  // ════════════════════════════ WITHDRAW ══════════════════════════════════════════════════════════

  @Transactional
  public void withdraw(Long playerId, Long siegeId){
    Siege s = sieges.findById(siegeId).orElseThrow(() -> new IllegalArgumentException("Siege not found"));
    City city = cities.findById(s.getCityId()).orElse(null);
    boolean besieger = Objects.equals(playerId, s.getBesiegingPlayerId());
    boolean owner = city!=null && Objects.equals(playerId, city.getPlayerId());
    if (s.getStatus()==SiegeStatus.ACTIVE){
      if (!besieger) throw new IllegalStateException("Only the besieger can call off an active siege");
    } else if (s.getStatus()==SiegeStatus.BROKEN){
      if (!besieger && !owner) throw new IllegalStateException("Only the besieger or the city owner can withdraw the leftover troops");
    } else throw new IllegalStateException("This siege has already resolved");

    Map<String,Integer> back = new LinkedHashMap<>();
    if (s.getBesiegingTroops()!=null) s.getBesiegingTroops().forEach((k,v)->back.merge(k,v,Integer::sum));
    if (s.getBesiegingShips()!=null)  s.getBesiegingShips().forEach((k,v)->back.merge(k,v,Integer::sum));
    Hero hero = s.getHeroId()!=null ? heroRepo.findById(s.getHeroId()).orElse(null) : null;
    sendReturn(s.getWorldId(), s.getOriginCityId(), s.getCityId(), back, hero, Instant.now());
    sieges.delete(s);
  }

  // ════════════════════════════ RACE CHOICE (after conquest) ══════════════════════════════════════

  @Transactional
  public void chooseRace(Long playerId, Long cityId, String raceName){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    if (!c.isConqueredPendingRace()) throw new IllegalStateException("This city has no pending race choice");
    Race race = Race.valueOf(raceName);
    c.setRace(race);
    c.setConqueredPendingRace(false);
    if (c.get(race.specialResource) <= 0) c.set(race.specialResource, 100);  // seed the new race's special resource
    cities.save(c);
  }

  // ════════════════════════════ READ VIEWS ════════════════════════════════════════════════════════

  @Transactional(readOnly = true)
  public Map<String,Object> siegeView(Long playerId, Long siegeId){
    Siege s = sieges.findById(siegeId).orElseThrow(() -> new IllegalArgumentException("Siege not found"));
    return view(playerId, s);
  }

  @Transactional(readOnly = true)
  public Map<String,Object> citySiege(Long playerId, Long cityId){
    Siege s = sieges.findByCityIdAndStatus(cityId, SiegeStatus.ACTIVE)
        .or(() -> sieges.findByCityIdAndStatus(cityId, SiegeStatus.BROKEN)).orElse(null);
    return s==null ? null : view(playerId, s);
  }

  @Transactional(readOnly = true)
  public List<Map<String,Object>> mySieges(Long playerId){
    List<Map<String,Object>> out = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    List<SiegeStatus> live = List.of(SiegeStatus.ACTIVE, SiegeStatus.BROKEN);
    // mine: sieges I'm running, and sieges laid on my own cities (defender perspective)
    for (SiegeStatus st : live)
      for (Siege s : sieges.findByBesiegingPlayerIdAndStatus(playerId, st))
        if (seen.add(s.getId())) out.add(view(playerId, s));
    for (City c : cities.findByPlayerId(playerId))
      for (SiegeStatus st : live)
        sieges.findByCityIdAndStatus(c.getId(), st).ifPresent(s -> { if (seen.add(s.getId())) out.add(view(playerId, s)); });

    // alliance: sieges my alliance is running, and sieges laid on any alliance member's city
    Long myAlliance = players.findById(playerId).map(Player::getAllianceId).orElse(null);
    if (myAlliance != null){
      for (SiegeStatus st : live)
        for (Siege s : sieges.findByBesiegingAllianceIdAndStatus(myAlliance, st))
          if (seen.add(s.getId())) out.add(view(playerId, s));
      for (SiegeStatus st : live)
        for (Siege s : sieges.findByStatus(st)){
          if (seen.contains(s.getId())) continue;
          City city = cities.findById(s.getCityId()).orElse(null);
          if (city == null || city.getPlayerId() == null) continue;
          boolean ownerInMyAlliance = players.findById(city.getPlayerId())
              .map(p -> Objects.equals(p.getAllianceId(), myAlliance)).orElse(false);
          if (ownerInMyAlliance && seen.add(s.getId())) out.add(view(playerId, s));
        }
    }
    return out;
  }

  /** The besieged city's id — but only if the caller is a party to the siege (besieger/owner or their ally). */
  @Transactional(readOnly = true)
  public Long participantCityId(Long playerId, Long siegeId){
    Siege s = sieges.findById(siegeId).orElseThrow(() -> new IllegalArgumentException("Siege not found"));
    City city = cities.findById(s.getCityId()).orElse(null);
    boolean ok = canBesiege(playerId, s) || (city != null && canDefend(playerId, city));
    if (!ok) throw new IllegalStateException("You are not a party to this siege");
    return s.getCityId();
  }

  private Map<String,Object> view(Long playerId, Siege s){
    City city = cities.findById(s.getCityId()).orElse(null);
    int troopsLeft = countCombatants(s.getBesiegingTroops(), CombatLayer.LAND);
    int shipsLeft  = countCombatants(s.getBesiegingShips(),  CombatLayer.SEA);
    Hero hero = s.getHeroId()!=null ? heroRepo.findById(s.getHeroId()).orElse(null) : null;
    boolean besieger = Objects.equals(playerId, s.getBesiegingPlayerId());
    boolean owner = city!=null && Objects.equals(playerId, city.getPlayerId());
    Player meP = players.findById(playerId).orElse(null);
    Long myAlliance = meP!=null ? meP.getAllianceId() : null;
    boolean allyOfBesieger = myAlliance!=null && Objects.equals(myAlliance, s.getBesiegingAllianceId());
    boolean allyOfOwner = myAlliance!=null && city!=null && city.getPlayerId()!=null
        && players.findById(city.getPlayerId()).map(p->Objects.equals(p.getAllianceId(), myAlliance)).orElse(false);

    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", s.getId());
    m.put("cityId", s.getCityId());
    m.put("cityName", city!=null ? city.getName() : "Unknown city");
    m.put("status", s.getStatus().name());
    m.put("startedAt", s.getStartedAt()!=null ? s.getStartedAt().toString() : null);
    m.put("endsAt", s.getEndsAt()!=null ? s.getEndsAt().toString() : null);
    m.put("besiegingPlayerId", s.getBesiegingPlayerId());
    m.put("besiegingPlayer", playerName(s.getBesiegingPlayerId()));
    m.put("defenderPlayerId", city!=null ? city.getPlayerId() : null);
    m.put("besiegingTroops", s.getBesiegingTroops());
    m.put("besiegingShips", s.getBesiegingShips());
    m.put("troopsRemaining", troopsLeft);
    m.put("shipsRemaining", shipsLeft);
    m.put("heroName", hero!=null ? hero.getName() : null);
    m.put("heroLevel", hero!=null ? hero.getLevel() : null);
    m.put("isBesieger", besieger);
    m.put("isDefender", owner);
    // an alliance siege from my POV = I'm neither the besieger nor the owner, but an ally on either side
    m.put("isAllianceSiege", !besieger && !owner && (allyOfBesieger || allyOfOwner));
    m.put("canReinforce", s.getStatus()==SiegeStatus.ACTIVE && (besieger || allyOfBesieger));
    m.put("canBreak", s.getStatus()==SiegeStatus.ACTIVE && (owner || allyOfOwner));
    m.put("canWithdraw", (s.getStatus()==SiegeStatus.ACTIVE && besieger)
        || (s.getStatus()==SiegeStatus.BROKEN && (besieger || owner)));
    return m;
  }

  // ════════════════════════════ helpers ═══════════════════════════════════════════════════════════

  private Siege active(Long siegeId){
    Siege s = sieges.findById(siegeId).orElseThrow(() -> new IllegalArgumentException("Siege not found"));
    if (s.getStatus()!=SiegeStatus.ACTIVE) throw new IllegalStateException("This siege is no longer active");
    return s;
  }
  private City owned(Long playerId, Long cityId){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    return c;
  }
  private boolean canBesiege(Long playerId, Siege s){
    if (Objects.equals(playerId, s.getBesiegingPlayerId())) return true;
    if (s.getBesiegingAllianceId()==null) return false;
    return players.findById(playerId).map(p->Objects.equals(p.getAllianceId(), s.getBesiegingAllianceId())).orElse(false);
  }
  private boolean canDefend(Long playerId, City city){
    if (city.getPlayerId()==null) return false;
    if (Objects.equals(playerId, city.getPlayerId())) return true;
    Player me = players.findById(playerId).orElse(null);
    if (me==null || me.getAllianceId()==null) return false;
    return players.findById(city.getPlayerId()).map(p->Objects.equals(p.getAllianceId(), me.getAllianceId())).orElse(false);
  }

  /** Combined attacker-attack / defender-defence multipliers from race + Library + the leading hero. */
  private CombatEngine.Mods battleMods(Race atkRace, LibraryService.LibEffects atkLib, Hero atkHero,
                                       Race defRace, LibraryService.LibEffects defLib, Hero defHero){
    double atkMult = (atkHero!=null ? heroService.offenseMods(atkHero).attackMult() : 1.0)
        * (atkRace!=null ? atkRace.attackMult : 1.0) * atkLib.attackMult();
    double defMult = (defHero!=null ? heroService.defenseMods(defHero).defenseMult() : 1.0)
        * (defRace!=null ? defRace.defenseMult : 1.0) * defLib.defenseMult();
    double aLoss = atkHero!=null ? heroService.offenseMods(atkHero).attackerLossMult() : 1.0;
    return new CombatEngine.Mods(atkMult, defMult,
        defLib.defFireMult(), defLib.defWindMult(), defLib.defEarthMult(), defLib.defWaterMult(), aLoss);
  }

  private Map<String,Integer> mergeDefenders(List<CityUnit> garrison, List<Reinforcement> reinf){
    Map<String,Integer> d = new LinkedHashMap<>();
    for (CityUnit cu : garrison) if (cu.getCount()>0) d.merge(cu.getType().toUpperCase(), cu.getCount(), Integer::sum);
    for (Reinforcement r : reinf) if (r.getUnits()!=null) for (var e : r.getUnits().entrySet())
      if (e.getValue()!=null && e.getValue()>0) d.merge(e.getKey().toUpperCase(), e.getValue(), Integer::sum);
    return d;
  }

  /** Proportional defender-loss distribution across garrison + reinforcements (mirrors TickScheduler). */
  private void applyDefenderLosses(List<CityUnit> garrison, List<Reinforcement> reinf, Map<String,Integer> lost){
    for (var e : lost.entrySet()){
      String type = e.getKey(); int remaining = e.getValue()==null?0:e.getValue();
      if (remaining<=0) continue;
      int total=0;
      for (CityUnit cu : garrison) if (cu.getType().equalsIgnoreCase(type)) total += cu.getCount();
      for (Reinforcement r : reinf) total += r.getUnits().getOrDefault(type, 0);
      if (total<=0) continue;
      for (Reinforcement r : reinf){
        int have = r.getUnits().getOrDefault(type, 0);
        if (have<=0) continue;
        int take = Math.min(have, (int)Math.floor((double)e.getValue()*have/total));
        if (take>0){ r.getUnits().put(type, have-take); remaining-=take; }
      }
      for (CityUnit cu : garrison){
        if (remaining<=0) break;
        if (!cu.getType().equalsIgnoreCase(type)) continue;
        int take = Math.min(cu.getCount(), remaining); cu.setCount(cu.getCount()-take); remaining-=take;
      }
      for (Reinforcement r : reinf){
        if (remaining<=0) break;
        int have = r.getUnits().getOrDefault(type, 0);
        int take = Math.min(have, remaining);
        if (take>0){ r.getUnits().put(type, have-take); remaining-=take; }
      }
    }
    for (CityUnit cu : garrison) units.save(cu);
    for (Reinforcement r : reinf){
      r.getUnits().values().removeIf(v -> v==null || v<=0);
      if (r.getUnits().isEmpty()) reinforcements.delete(r); else reinforcements.save(r);
    }
  }

  /** Create a RETURN movement marching units (and an optional hero) home to {@code homeCityId}. */
  private void sendReturn(Long worldId, Long homeCityId, Long fromCityId, Map<String,Integer> unitsMap, Hero hero, Instant now){
    Map<String,Integer> u = new LinkedHashMap<>();
    if (unitsMap!=null) unitsMap.forEach((k,v)->{ if (v!=null && v>0) u.merge(k,v,Integer::sum); });
    if (u.isEmpty() && hero==null) return;
    City home = cities.findById(homeCityId).orElse(null);
    long secs = home!=null ? Math.max(5, travel.travelTime(homeCityId, fromCityId, u).getSeconds()) : 60;
    Movement ret = new Movement();
    ret.setWorldId(worldId); ret.setSourceCityId(homeCityId); ret.setTargetCityId(fromCityId);
    ret.setPhase(MovementPhase.RETURN); ret.setUnits(u); ret.setArriveAt(now.plusSeconds(secs));
    Movement saved = movements.save(ret);
    if (hero != null){ hero.setState(HeroState.MARCHING); hero.setActiveMovementId(saved.getId()); heroRepo.save(hero); }
  }

  private void freeHeroIdle(Siege s, Long stationCityId){
    if (s.getHeroId()==null) return;
    heroRepo.findById(s.getHeroId()).ifPresent(h -> {
      h.setState(HeroState.IDLE);
      h.setActiveMovementId(null);
      if (stationCityId!=null) h.setStationedCityId(stationCityId);
      heroRepo.save(h);
    });
  }

  private long travelSecs(City src, Long fromCityId, Long toCityId, Map<String,Integer> army){
    long secs = travel.travelTime(fromCityId, toCityId, army).getSeconds();
    if (src.getRace()!=null) secs = (long)(secs * src.getRace().travelMult);
    secs = (long)(secs * library.effects(fromCityId).travelMult());
    return secs;
  }

  private int countCombatants(Map<String,Integer> map, CombatLayer layer){
    int n=0; for (var v : combat.combatants(map==null?Map.of():map, layer).values()) n += v; return n;
  }
  /** True if the attacker was gutted (>70% of the force sent was lost → wound the hero). */
  private boolean countWounds(CombatEngine.Result r){
    int sent = sum(r.attackerLost()) + sum(r.attackerSurvived());
    return sent>0 && (double)sum(r.attackerLost())/sent > HeroService.WOUND_THRESHOLD;
  }
  private static int sum(Map<String,Integer> m){ int n=0; if (m!=null) for (var v : m.values()) if (v!=null) n+=v; return n; }
  private static Map<String,Long> emptyLoot(){ Map<String,Long> l=new LinkedHashMap<>(); l.put("WOOD",0L); l.put("STONE",0L); l.put("WHEAT",0L); return l; }

  private String playerName(Long id){ return id==null?null:players.findById(id).map(Player::getUsername).orElse("Unknown"); }
  private void notify(Long besieger, Long defender, String toBesieger, String toDefender){
    if (besieger!=null){ Message a=new Message(); a.setToPlayerId(besieger); a.setBody(toBesieger); messages.save(a); }
    if (defender!=null){ Message b=new Message(); b.setToPlayerId(defender); b.setBody(toDefender); messages.save(b); }
  }
}
