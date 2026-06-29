package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * BANDIT TOWER — an account-wide, 100-level PvE climb (one per player, forward-only).
 *
 * <p>Each level is an instant elemental fight (no map travel) against a defending force that scales
 * up with level. Losses are real on both sides. The defenders' survivors PERSIST across attempts
 * ({@link BanditTowerProgress#getCurrentLevelDefenders()}), so a player without a one-shot army wears
 * a level down over successive waves. Clearing a level (defenders fully defeated) grants its reward
 * once and unlocks the next. Milestone levels (every 10th) give bigger rewards; high milestones drop
 * a hero item with rarity rising toward level 100.
 */
@Service
public class BanditTowerService {
  private final BanditTowerProgressRepo progressRepo;
  private final CityRepo cities;
  private final UnitRepo units;
  private final CityService cityService;
  private final CombatEngine combat;
  private final UnitCatalog catalog;
  private final HeroService heroService;
  private final HeroItemRepo heroItems;
  private final ItemFactory itemFactory;
  private final BattleReportService reports;
  private final MissionService missions;
  private final Random rnd = new Random();

  public BanditTowerService(BanditTowerProgressRepo progressRepo, CityRepo cities, UnitRepo units,
                            CityService cityService, CombatEngine combat, UnitCatalog catalog,
                            HeroService heroService, HeroItemRepo heroItems, ItemFactory itemFactory,
                            BattleReportService reports, MissionService missions){
    this.progressRepo=progressRepo; this.cities=cities; this.units=units; this.cityService=cityService;
    this.combat=combat; this.catalog=catalog; this.heroService=heroService; this.heroItems=heroItems;
    this.itemFactory=itemFactory; this.reports=reports; this.missions=missions;
  }

  /** Get (or create) the player's tower progress, seeding the current level's defenders if needed. */
  @Transactional
  public BanditTowerProgress ensureProgress(Long playerId){
    BanditTowerProgress p = progressRepo.findByPlayerId(playerId)
        .orElseGet(() -> progressRepo.save(new BanditTowerProgress(playerId)));
    if (!p.isCurrentLevelInitialized() && p.getCurrentLevel() <= BanditTowerCatalog.MAX_LEVEL){
      p.setCurrentLevelDefenders(new LinkedHashMap<>(BanditTowerCatalog.defendersFor(p.getCurrentLevel())));
      p.setCurrentLevelInitialized(true);
      progressRepo.save(p);
    }
    return p;
  }

  /** Full tower state: current level, climb progress, this level's defenders (full + survivors), rewards. */
  @Transactional
  public Map<String,Object> state(Long playerId){
    BanditTowerProgress p = ensureProgress(playerId);
    int lvl = p.getCurrentLevel();
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("currentLevel", lvl);
    m.put("highestCleared", p.getHighestCleared());
    m.put("maxLevel", BanditTowerCatalog.MAX_LEVEL);
    boolean topped = lvl > BanditTowerCatalog.MAX_LEVEL;
    m.put("complete", topped);
    if (!topped){
      m.put("isMilestone", BanditTowerCatalog.isMilestone(lvl));
      m.put("resistedElement", BanditTowerCatalog.resistedElement(lvl));
      m.put("defendersFull", BanditTowerCatalog.defendersFor(lvl));
      m.put("defendersRemaining", p.getCurrentLevelDefenders());
      m.put("reward", rewardPreview(lvl));
    }
    // next milestone teaser
    Integer nextMs = null;
    for (int L = Math.max(lvl, 1); L <= BanditTowerCatalog.MAX_LEVEL; L++)
      if (BanditTowerCatalog.isMilestone(L)){ nextMs = L; break; }
    if (nextMs != null){
      Map<String,Object> ms = rewardPreview(nextMs);
      ms.put("level", nextMs);
      m.put("nextMilestone", ms);
    }
    return m;
  }

  /** Roadmap of all 100 levels: cleared / current / locked, milestone flag, reward preview. */
  @Transactional
  public List<Map<String,Object>> levels(Long playerId){
    BanditTowerProgress p = ensureProgress(playerId);
    List<Map<String,Object>> out = new ArrayList<>();
    for (int L = 1; L <= BanditTowerCatalog.MAX_LEVEL; L++){
      Map<String,Object> row = new LinkedHashMap<>();
      row.put("level", L);
      row.put("status", L <= p.getHighestCleared() ? "CLEARED" : (L == p.getCurrentLevel() ? "CURRENT" : "LOCKED"));
      row.put("isMilestone", BanditTowerCatalog.isMilestone(L));
      row.put("resistedElement", BanditTowerCatalog.resistedElement(L));
      row.put("reward", rewardPreview(L));
      out.add(row);
    }
    return out;
  }

  /** Attack the current level with troops (+ optional hero). Real losses; persistent defender damage. */
  @Transactional
  public Map<String,Object> attack(Long playerId, Long fromCityId, Map<String,Integer> troops, Long heroId){
    City city = cities.findById(fromCityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(city.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    cityService.sync(city);
    if (troops == null || troops.isEmpty()) throw new IllegalArgumentException("Select at least one unit");

    BanditTowerProgress p = ensureProgress(playerId);
    int level = p.getCurrentLevel();
    if (level > BanditTowerCatalog.MAX_LEVEL) throw new IllegalStateException("You have already topped the Bandit Tower");

    // validate troops against the city garrison
    Map<String,Integer> garrison = new HashMap<>();
    for (CityUnit cu : units.findByCityId(fromCityId)) garrison.merge(cu.getType().toUpperCase(), cu.getCount(), Integer::sum);
    Map<String,Integer> sent = new LinkedHashMap<>();
    for (var e : troops.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      String name = catalog.get(e.getKey()).getName();
      if (garrison.getOrDefault(name, 0) < e.getValue()) throw new IllegalStateException("Not enough " + name);
      sent.put(name, e.getValue());
    }
    if (sent.isEmpty()) throw new IllegalArgumentException("Select at least one unit");

    // current (persistent) defenders — survivors of any prior waves
    Map<String,Integer> defenders = new LinkedHashMap<>();
    for (var e : p.getCurrentLevelDefenders().entrySet())
      if (e.getValue() != null && e.getValue() > 0) defenders.put(e.getKey().toUpperCase(), e.getValue());
    if (defenders.isEmpty()) defenders = new LinkedHashMap<>(BanditTowerCatalog.defendersFor(level)); // safety re-seed

    // combat mods: city race attack + optional idle hero offense (instant fight)
    Hero hero = null;
    double atkMult = city.getRace() != null ? city.getRace().attackMult : 1.0;
    if (heroId != null){
      hero = heroService.requireOwned(playerId, heroId);
      if (!hero.isUnlocked()) throw new IllegalStateException("That hero is not unlocked");
      if (hero.getState() != HeroState.IDLE || !Objects.equals(hero.getStationedCityId(), fromCityId))
        throw new IllegalStateException("That hero must be idle in this city");
      atkMult *= heroService.offenseMods(hero).attackMult();
    }
    CombatEngine.Mods mods = new CombatEngine.Mods(atkMult, 1, 1, 1, 1, 1, 1);
    CombatEngine.CombatFx fx = hero != null ? heroService.combatFx(hero) : CombatEngine.CombatFx.none();
    Element atkElement = city.getRace() != null ? city.getRace().element : Element.FIRE;
    double heroAtk = hero != null ? heroService.baseAttack(hero) : 0;

    Map<String,Integer> defendersBefore = new LinkedHashMap<>(defenders);
    CombatEngine.Result r = combat.resolve(sent, atkElement, defenders, mods, fx, heroAtk);

    // REAL losses: attacker casualties leave the city garrison
    deductLosses(fromCityId, r.attackerLost());

    // defender survivors PERSIST — store them back as the level's live force
    Map<String,Integer> survivors = new LinkedHashMap<>();
    for (var e : r.defenderSurvived().entrySet())
      if (e.getValue() != null && e.getValue() > 0) survivors.put(e.getKey().toUpperCase(), e.getValue());
    boolean cleared = survivors.isEmpty();

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("level", level);
    out.put("outcome", cleared ? "CLEARED" : "REPELLED");
    out.put("troopsLost", r.attackerLost());
    out.put("defendersDefeated", r.defenderLost());
    out.put("defendersRemaining", survivors);

    if (cleared){
      Map<String,Object> reward = grantReward(playerId, city, level);
      out.put("reward", reward);
      out.put("cleared", true);
      // climb: mark cleared, advance, seed the next level at full strength
      p.setHighestCleared(Math.max(p.getHighestCleared(), level));
      int next = level + 1;
      p.setCurrentLevel(next);
      if (next <= BanditTowerCatalog.MAX_LEVEL){
        p.setCurrentLevelDefenders(new LinkedHashMap<>(BanditTowerCatalog.defendersFor(next)));
        p.setCurrentLevelInitialized(true);
      } else {
        p.setCurrentLevelDefenders(new LinkedHashMap<>());
        p.setCurrentLevelInitialized(true);
        out.put("towerComplete", true);
      }
      // starter-mission hook: clearing a tower level satisfies the old "defeat a bandit" objective
      missions.record(playerId, MissionObjectiveType.ATTACK_BANDIT_CAMP, 1);
    } else {
      p.setCurrentLevelDefenders(survivors);
      p.setCurrentLevelInitialized(true);
      out.put("cleared", false);
    }
    progressRepo.save(p);
    out.put("currentLevel", p.getCurrentLevel());
    out.put("highestCleared", p.getHighestCleared());

    // hero XP on a clear, and a wound if the army was gutted
    HeroParticipation hp = null;
    if (hero != null){
      int xp = cleared ? 30 * level : 0;
      if (xp > 0){ heroService.grantXp(playerId, hero.getId(), xp); out.put("heroXp", xp); }
      int sentPop = popOf(r.attackerLost()) + popOf(r.attackerSurvived());
      boolean wounded = sentPop > 0 && (double) popOf(r.attackerLost()) / sentPop > HeroService.WOUND_THRESHOLD;
      if (wounded) heroService.wound(hero, java.time.Instant.now());
      hp = new HeroParticipation(hero.getName(), hero.getLevel(),
          heroService.attackBonusPct(hero), heroService.lossReductionPct(hero), null, xp, null, wounded);
    }

    // Battle Report (instant PvE, no marching movement). Resources shown only if the level was cleared.
    Map<String,Long> resourcesGained = new LinkedHashMap<>();
    resourcesGained.put("WOOD", 0L); resourcesGained.put("STONE", 0L); resourcesGained.put("WHEAT", 0L);
    if (cleared) for (var e : BanditTowerCatalog.rewardFor(level).resources().entrySet())
      resourcesGained.merge(e.getKey().toUpperCase(), e.getValue(), Long::sum);
    BattleResult res = new BattleResult(r.outcome(), sent, r.attackerLost(), r.attackerSurvived(),
        defendersBefore, r.defenderLost(), r.defenderSurvived(), resourcesGained,
        r.attackerAttackPower(), r.defenderDefencePower(), r.siegeDamage(),
        r.attackByElement(), r.defenseByElement());
    reports.createPveReport(city.getWorldId(), playerId, fromCityId,
        "🏰 Bandit Tower · Lv " + level, res, hp);

    return out;
  }

  // ---- helpers ----

  private Map<String,Object> rewardPreview(int level){
    BanditTowerCatalog.Reward rw = BanditTowerCatalog.rewardFor(level);
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("headline", rw.headline());
    m.put("resources", rw.resources());
    m.put("troops", rw.troops());
    m.put("itemRarity", rw.itemRarity() == null ? null : rw.itemRarity().name());
    return m;
  }

  /** Apply a cleared level's reward: resources + troops to the city, a hero item to the inventory. */
  private Map<String,Object> grantReward(Long playerId, City city, int level){
    BanditTowerCatalog.Reward rw = BanditTowerCatalog.rewardFor(level);
    Map<String,Object> g = new LinkedHashMap<>();
    if (!rw.resources().isEmpty()){
      long cap = cityService.capacity(city.getId());
      Map<String,Long> got = new LinkedHashMap<>();
      for (var e : rw.resources().entrySet()){
        ResourceType rt; try { rt = ResourceType.valueOf(e.getKey()); } catch (Exception ex){ continue; }
        city.set(rt, Math.min(cap, city.get(rt) + e.getValue()));
        got.put(e.getKey(), e.getValue());
      }
      cities.save(city);
      g.put("resources", got);
    }
    if (!rw.troops().isEmpty()){
      for (var e : rw.troops().entrySet()){
        if (e.getValue() == null || e.getValue() <= 0) continue;
        String name = catalog.get(e.getKey()).getName();
        CityUnit cu = units.findByCityId(city.getId()).stream()
            .filter(u -> u.getType().equalsIgnoreCase(name)).findFirst()
            .orElseGet(() -> new CityUnit(city.getId(), name, 0));
        cu.setCount(cu.getCount() + e.getValue()); units.save(cu);
      }
      g.put("troops", rw.troops());
    }
    if (rw.itemRarity() != null){
      HeroItem item = itemFactory.ofRarity(playerId, rw.itemRarity(), rnd);
      heroItems.save(item);
      g.put("item", Map.of("name", item.getName(), "rarity", item.getRarity().name(),
          "slot", item.getSlot().name(), "buffs", item.getBuffs()));
    }
    return g;
  }

  private void deductLosses(Long cityId, Map<String,Integer> lost){
    for (var e : lost.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      String name = catalog.get(e.getKey()).getName();
      units.findByCityId(cityId).stream().filter(u -> u.getType().equalsIgnoreCase(name)).findFirst()
          .ifPresent(cu -> { cu.setCount(Math.max(0, cu.getCount() - e.getValue())); units.save(cu); });
    }
  }

  private int popOf(Map<String,Integer> a){
    int s = 0; for (var e : a.entrySet()) s += catalog.get(e.getKey()).getPopulationCost() * e.getValue(); return s;
  }
}
