package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Per-island bandit camps. Levels are beaten in order; clearing level 10 puts the camp into a
 * respawn cooldown, after which it returns to level 1. Combat reuses the type-based engine;
 * losses are deducted from the attacking city and rewards granted to it immediately.
 */
@Service
public class BanditCampService {
  private final BanditCampRepo camps;
  private final BanditCampLevelRepo levels;
  private final CityRepo cities;
  private final UnitRepo units;
  private final CityService cityService;
  private final CombatEngine combat;
  private final UnitCatalog catalog;
  private final MissionService missions;

  @Value("${polis.bandit-respawn-hours:24}") private long respawnHours;

  public BanditCampService(BanditCampRepo camps, BanditCampLevelRepo levels, CityRepo cities, UnitRepo units,
                           CityService cityService, CombatEngine combat, UnitCatalog catalog, MissionService missions){
    this.camps=camps; this.levels=levels; this.cities=cities; this.units=units;
    this.cityService=cityService; this.combat=combat; this.catalog=catalog; this.missions=missions;
  }

  @Transactional
  public BanditCamp campFor(Long islandId){
    BanditCamp c = camps.findByIslandId(islandId).orElseGet(() -> {
      BanditCamp n = new BanditCamp(); n.setIslandId(islandId); n.setCurrentLevel(1); return camps.save(n);
    });
    // respawn if the cooldown has elapsed
    if (c.getRespawnAt() != null && !c.getRespawnAt().isAfter(Instant.now())){
      c.setCurrentLevel(1); c.setRespawnAt(null); c.setDefeatedAt(null); camps.save(c);
    }
    return c;
  }

  @Transactional
  public Map<String,Object> dto(Long islandId){
    BanditCamp c = campFor(islandId);
    boolean defeated = c.getRespawnAt() != null;
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("islandId", islandId);
    m.put("currentLevel", c.getCurrentLevel());
    m.put("maxLevel", 10);
    m.put("status", defeated ? "DEFEATED" : "ACTIVE");
    m.put("respawnAt", c.getRespawnAt() == null ? null : c.getRespawnAt().toString());
    if (!defeated){
      BanditCampLevel lv = level(c.getCurrentLevel());
      m.put("defenderTroops", lv.getDefenderTroops());
      m.put("rewardType", lv.getRewardType().name());
      m.put("rewardPayload", lv.getRewardPayload());
      m.put("description", lv.getDescription());
    }
    return m;
  }

  private BanditCampLevel level(int lv){
    return levels.findById(lv).orElseThrow(() -> new IllegalStateException("Camp level not configured: " + lv));
  }

  @Transactional
  public Map<String,Object> attack(Long playerId, Long islandId, Long cityId, Map<String,Integer> troops){
    City city = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(city.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    if (!Objects.equals(city.getIslandId(), islandId)) throw new IllegalStateException("That city is not on this island");
    if (troops == null || troops.isEmpty()) throw new IllegalArgumentException("Select at least one unit");

    BanditCamp camp = campFor(islandId);
    if (camp.getRespawnAt() != null) throw new IllegalStateException("The camp has been defeated — it is rebuilding");

    // validate the city actually has the troops being sent
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

    BanditCampLevel lv = level(camp.getCurrentLevel());
    CombatEngine.Result r = combat.resolve(sent, lv.getDefenderTroops(), CombatEngine.Mods.none());
    boolean win = r.outcome() == BattleOutcome.VICTORY;

    // deduct losses from the city garrison (on a loss the engine already marks the whole army lost)
    deductLosses(cityId, r.attackerLost());

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("outcome", win ? "WIN" : "LOSS");
    out.put("troopsLost", r.attackerLost());
    if (win){
      missions.record(playerId, MissionObjectiveType.ATTACK_BANDIT_CAMP, 1);
      Map<String,Object> reward = grantReward(city, lv);
      out.put("reward", reward);
      if (camp.getCurrentLevel() >= 10){
        camp.setDefeatedAt(Instant.now());
        camp.setRespawnAt(Instant.now().plusSeconds(respawnHours * 3600));
      } else {
        camp.setCurrentLevel(camp.getCurrentLevel() + 1);
      }
      camps.save(camp);
    }
    out.put("newCampLevel", camp.getCurrentLevel());
    out.put("nextRespawnAt", camp.getRespawnAt() == null ? null : camp.getRespawnAt().toString());
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

  private Map<String,Object> grantReward(City city, BanditCampLevel lv){
    long cap = cityService.capacity(city.getId());
    Map<String,Object> granted = new LinkedHashMap<>();
    for (var e : lv.getRewardPayload().entrySet()){
      String key = e.getKey(); int amt = e.getValue();
      switch (key.toLowerCase()){
        case "wood"   -> { city.setWood(Math.min(cap, city.getWood() + amt)); granted.put("wood", amt); }
        case "stone"  -> { city.setStone(Math.min(cap, city.getStone() + amt)); granted.put("stone", amt); }
        case "silver" -> { city.setSilver(Math.min(cap, city.getSilver() + amt)); granted.put("silver", amt); }
        default -> {
          String name = catalog.get(key).getName();
          CityUnit cu = units.findByCityId(city.getId()).stream().filter(u -> u.getType().equalsIgnoreCase(name)).findFirst()
              .orElseGet(() -> new CityUnit(city.getId(), name, 0));
          cu.setCount(cu.getCount() + amt); units.save(cu);
          granted.put(name, amt);
        }
      }
    }
    cities.save(city);
    return granted;
  }
}
