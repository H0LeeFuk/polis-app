package com.polis.game;

import com.polis.domain.BuildingType;
import com.polis.domain.City;
import com.polis.domain.CityBuilding;
import com.polis.domain.Player;
import com.polis.repo.BuildingRepo;
import com.polis.repo.CityRepo;
import com.polis.repo.PlayerRepo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * NPC / barbarian cities are seeded with resources but NO buildings (just a points/power value), so
 * a spy on them revealed an empty building list. This gives every NPC-owned (or ownerless barbarian)
 * city a realistic building profile scaled by its points, so scouting always shows buildings.
 *
 * <p>Runs after {@link CityBuildingBackfillRunner} (which guarantees a row per building type exists).
 * Idempotent: a city that already has any building above level 0 is left untouched, so real players'
 * cities and already-fixed NPC cities are never altered.
 */
@Component
@Order(27)
public class NpcCityBuildingBackfillRunner implements ApplicationRunner {
  private final CityRepo cities;
  private final BuildingRepo buildings;
  private final PlayerRepo players;

  public NpcCityBuildingBackfillRunner(CityRepo cities, BuildingRepo buildings, PlayerRepo players){
    this.cities = cities; this.buildings = buildings; this.players = players;
  }

  @Override @Transactional
  public void run(ApplicationArguments args){
    for (City c : cities.findAll()){
      Long owner = c.getPlayerId();
      boolean npc = owner == null || players.findById(owner).map(Player::isNpc).orElse(true);
      if (!npc) continue;
      List<CityBuilding> rows = buildings.findByCityId(c.getId());
      int maxLv = rows.stream().mapToInt(CityBuilding::getLevel).max().orElse(0);
      if (maxLv > 0) continue;   // already developed (real city, or fixed on a prior boot)
      Map<BuildingType,Integer> profile = profileFor(c.getPoints());
      for (CityBuilding b : rows){
        int set = profile.getOrDefault(b.getType(), 0);
        if (set > 0){ b.setLevel(Math.min(set, b.getType().max)); buildings.save(b); }
      }
    }
  }

  /** A believable building spread for an NPC city, scaled by its points (bigger city = higher levels). */
  private Map<BuildingType,Integer> profileFor(int points){
    int lv = Math.max(1, Math.min(14, points / 35));   // core level
    Map<BuildingType,Integer> m = new EnumMap<>(BuildingType.class);
    m.put(BuildingType.SENATE, lv);
    m.put(BuildingType.WAREHOUSE, lv);
    m.put(BuildingType.FARM, lv);
    m.put(BuildingType.TIMBER, Math.max(1, lv - 1));
    m.put(BuildingType.QUARRY, Math.max(1, lv - 1));
    m.put(BuildingType.MINE, Math.max(1, lv - 1));
    m.put(BuildingType.EXTRACTOR, Math.max(1, lv - 2));
    m.put(BuildingType.BARRACKS, Math.max(1, lv - 2));
    m.put(BuildingType.MARKET, Math.max(0, lv - 3));
    m.put(BuildingType.ALTAR, Math.max(0, lv - 3));
    m.put(BuildingType.LIBRARY, Math.max(0, lv - 4));
    m.put(BuildingType.HARBOR, Math.max(0, lv - 5));
    m.put(BuildingType.WATCHTOWER, Math.max(0, lv - 4));
    return m;
  }
}
