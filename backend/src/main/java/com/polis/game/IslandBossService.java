package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Resource-island guardian bosses. An instant PvE fight (like bandit camps) launched from a
 * city on the island. On victory the city gains resources and the player gains a guaranteed
 * RARE relic — bosses are the only rare-drop source now (resource nodes give resources only).
 * Defeated bosses respawn after a cooldown.
 */
@Service
public class IslandBossService {
  private final IslandBossRepo bosses;
  private final CityRepo cities;
  private final UnitRepo units;
  private final CityService cityService;
  private final CombatEngine combat;
  private final UnitCatalog catalog;
  private final HeroService heroService;
  private final HeroItemRepo heroItems;
  private final ItemFactory itemFactory;
  private final BattleReportService reports;
  private final Random rnd = new Random();

  @Value("${polis.boss-respawn-hours:12}") private long respawnHours;

  public IslandBossService(IslandBossRepo bosses, CityRepo cities, UnitRepo units, CityService cityService,
                           CombatEngine combat, UnitCatalog catalog, HeroService heroService,
                           HeroItemRepo heroItems, ItemFactory itemFactory, BattleReportService reports){
    this.bosses=bosses; this.cities=cities; this.units=units; this.cityService=cityService;
    this.combat=combat; this.catalog=catalog; this.heroService=heroService;
    this.heroItems=heroItems; this.itemFactory=itemFactory; this.reports=reports;
  }

  /** Resources granted per boss level on a win. */
  private static final long RESOURCE_PER_LEVEL = 1200L;

  /**
   * Boss relic drop table (rare overall): ~2% a COMMON relic, ~0.5% RARE, ~0.2% EPIC — anything
   * else is no drop. Bosses are the only relic source. Returns null when nothing drops.
   */
  private static HeroItem.Rarity rollBossDropRarity(Random rnd){
    double r = rnd.nextDouble();
    if (r < 0.002) return HeroItem.Rarity.EPIC;     // 0.2%
    if (r < 0.007) return HeroItem.Rarity.RARE;     // 0.5%
    if (r < 0.027) return HeroItem.Rarity.COMMON;   // 2%
    return null;                                     // ~97.3% no relic
  }

  /** Race-themed defenders scaled by level (uses the always-present Human base roster). */
  public static Map<String,Integer> defendersFor(int level){
    Map<String,Integer> d = new LinkedHashMap<>();
    d.put("HOPLITE", 40 * level);
    d.put("SPEARMAN", 24 * level);
    d.put("ARCHER", 20 * level);
    d.put("HORSEMAN", 8 * level);
    return d;
  }

  private static final Map<Race,String> BOSS_NAME = Map.of(
      Race.HUMANS, "Warden of the Vale",
      Race.GIANTS, "Gravelmaw the Titan",
      Race.FAIRIES, "Thistlewing Queen",
      Race.NEWTS, "Tidemother Saru");

  public String bossName(Race r){ return BOSS_NAME.getOrDefault(r, "Island Guardian"); }

  @Transactional
  public IslandBoss settle(IslandBoss b){
    if (b.getRespawnAt() != null && !b.getRespawnAt().isAfter(Instant.now())){
      b.setRespawnAt(null); b.setDefeatedAt(null); bosses.save(b);
    }
    return b;
  }

  @Transactional(readOnly = false)
  public Map<String,Object> dto(Long islandId){
    IslandBoss b = bosses.findByIslandId(islandId).orElse(null);
    if (b == null) return Map.of("exists", false);
    settle(b);
    boolean defeated = b.getRespawnAt() != null;
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("exists", true);
    m.put("islandId", islandId);
    m.put("name", b.getName());
    m.put("race", b.getRace().name());
    m.put("level", b.getLevel());
    m.put("status", defeated ? "DEFEATED" : "ACTIVE");
    m.put("respawnAt", b.getRespawnAt() == null ? null : b.getRespawnAt().toString());
    if (!defeated) m.put("defenderTroops", defendersFor(b.getLevel()));
    return m;
  }

  @Transactional
  public Map<String,Object> attack(Long playerId, Long islandId, Long cityId, Map<String,Integer> troops, Long heroId){
    City city = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(city.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    // resource islands host no player cities, so the strike may launch from any of your cities
    IslandBoss b = bosses.findByIslandId(islandId).orElseThrow(() -> new IllegalStateException("No boss on this island"));
    settle(b);
    if (b.getRespawnAt() != null) throw new IllegalStateException("The boss is defeated — it returns later");
    if (troops == null || troops.isEmpty()) throw new IllegalArgumentException("Select at least one unit");

    // validate troops against the city garrison
    Map<String,Integer> garrison = new HashMap<>();
    for (CityUnit cu : units.findByCityId(cityId)) garrison.merge(cu.getType().toUpperCase(), cu.getCount(), Integer::sum);
    Map<String,Integer> sent = new LinkedHashMap<>();
    for (var e : troops.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      String name = catalog.get(e.getKey()).getName();
      if (garrison.getOrDefault(name, 0) < e.getValue()) throw new IllegalStateException("Not enough " + name);
      sent.put(name, e.getValue());
    }
    if (sent.isEmpty()) throw new IllegalArgumentException("Select at least one unit");

    // combat mods: city race attack + optional hero offense (instant fight, hero must be idle here)
    Hero hero = null;
    double atkMult = city.getRace() != null ? city.getRace().attackMult : 1.0;
    if (heroId != null){
      hero = heroService.requireOwned(playerId, heroId);
      if (!hero.isUnlocked()) throw new IllegalStateException("That hero is not unlocked");
      if (hero.getState() != HeroState.IDLE || !Objects.equals(hero.getStationedCityId(), cityId))
        throw new IllegalStateException("That hero must be idle in this city");
      atkMult *= heroService.offenseMods(hero).attackMult();
    }
    CombatEngine.Mods mods = new CombatEngine.Mods(atkMult, 1, 1, 1);
    CombatEngine.CombatFx fx = hero != null ? heroService.combatFx(hero) : CombatEngine.CombatFx.none();
    // defenders computed live from the boss level so difficulty tuning applies to all bosses
    Map<String,Integer> defendersSnapshot = defendersFor(b.getLevel());
    CombatEngine.Result r = combat.resolve(sent, defendersSnapshot, mods, fx);
    boolean win = r.outcome() == BattleOutcome.VICTORY;

    deductLosses(cityId, r.attackerLost());

    // resources the city gains on a win — also shown on the battle report
    Map<String,Long> resourcesStolen = new LinkedHashMap<>();
    resourcesStolen.put("WOOD", 0L); resourcesStolen.put("STONE", 0L); resourcesStolen.put("SILVER", 0L);
    if (win){ long amt = RESOURCE_PER_LEVEL * b.getLevel(); resourcesStolen.replaceAll((k,v) -> amt); }

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("outcome", win ? "WIN" : "LOSS");
    out.put("troopsLost", r.attackerLost());
    if (win){
      Map<String,Object> reward = grantReward(city, b.getLevel());
      // relics are a rare drop — most kills yield none; epics are very rare
      HeroItem.Rarity dropRarity = rollBossDropRarity(rnd);
      if (dropRarity != null){
        HeroItem relic = itemFactory.ofRarity(playerId, dropRarity, rnd);
        heroItems.save(relic);
        reward.put("relic", Map.of("name", relic.getName(), "rarity", relic.getRarity().name(),
            "slot", relic.getSlot().name(), "buffs", relic.getBuffs()));
      }
      out.put("reward", reward);
      if (hero != null){ heroService.grantXp(playerId, hero.getId(), 30L * b.getLevel()); out.put("heroXp", 30 * b.getLevel()); }
      b.setDefeatedAt(Instant.now());
      b.setRespawnAt(Instant.now().plusSeconds(respawnHours * 3600));
      bosses.save(b);
    }
    out.put("status", b.getRespawnAt() != null ? "DEFEATED" : "ACTIVE");
    out.put("respawnAt", b.getRespawnAt() == null ? null : b.getRespawnAt().toString());

    // every battle is recorded — write the boss Battle Report (no marching movement)
    HeroParticipation hp = hero == null ? null : new HeroParticipation(
        hero.getName(), hero.getLevel(),
        heroService.attackBonusPct(hero), heroService.lossReductionPct(hero),
        null, win ? 30 * b.getLevel() : 0, null, false);
    BattleResult res = new BattleResult(r.outcome(), sent, r.attackerLost(), r.attackerSurvived(),
        defendersSnapshot, r.defenderLost(), r.defenderSurvived(), resourcesStolen,
        r.attackerAttackPower(), r.defenderDefencePower(), r.siegeDamage());
    reports.createPveReport(city.getWorldId(), playerId, cityId,
        "👹 " + b.getName() + " · Lv " + b.getLevel(), res, hp);

    return out;
  }

  private void deductLosses(Long cityId, Map<String,Integer> lost){
    for (var e : lost.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      String name = catalog.get(e.getKey()).getName();
      units.findByCityId(cityId).stream().filter(u -> u.getType().equalsIgnoreCase(name)).findFirst()
          .ifPresent(cu -> { cu.setCount(Math.max(0, cu.getCount() - e.getValue())); units.save(cu); });
    }
  }

  private Map<String,Object> grantReward(City city, int level){
    long cap = cityService.capacity(city.getId());
    long amt = RESOURCE_PER_LEVEL * level;
    city.setWood(Math.min(cap, city.getWood() + amt));
    city.setStone(Math.min(cap, city.getStone() + amt));
    city.setSilver(Math.min(cap, city.getSilver() + amt));
    cities.save(city);
    Map<String,Object> g = new LinkedHashMap<>();
    g.put("wood", amt); g.put("stone", amt); g.put("silver", amt);
    return g;
  }
}
