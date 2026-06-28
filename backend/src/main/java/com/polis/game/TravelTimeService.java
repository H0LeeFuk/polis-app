package com.polis.game;

import com.polis.domain.City;
import com.polis.domain.Island;
import com.polis.repo.CityRepo;
import com.polis.repo.IslandRepo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Single source of truth for how long troops take to travel between two cities.
 * Distance is the world-map pixel distance between the two islands; the journey
 * is paced by the SLOWEST unit in the group (a catapult drags the army down).
 *
 * <p>Pace is the unit's {@code speedMinutesPerTile} (higher = slower), so the
 * slowest unit is the one with the LARGEST value.
 */
@Service
public class TravelTimeService {
  /** Pixels per map tile — the world canvas is laid out in raw pixels. */
  private static final double PX_PER_TILE = 12.0;
  /** Seconds of travel per (tile × minute-per-tile). Tunes overall march pace. */
  private static final double SECONDS_PER_TILE_MINUTE = 0.05;
  /** Pace used for colony ships and any group with no resolvable units. */
  public static final int DEFAULT_MINUTES_PER_TILE = 20;
  /** Floor so same-island raids still take a sensible march. */
  private static final double MIN_DISTANCE = 8.0;

  private final IslandRepo islands;
  private final CityRepo cities;
  private final UnitCatalog catalog;

  public TravelTimeService(IslandRepo islands, CityRepo cities, UnitCatalog catalog){
    this.islands = islands; this.cities = cities; this.catalog = catalog;
  }

  /** Distance in tiles between two islands (floored to MIN_DISTANCE). */
  public double distanceTiles(Long fromIslandId, Long toIslandId){
    Island a = islands.findById(fromIslandId).orElseThrow();
    Island b = islands.findById(toIslandId).orElseThrow();
    return Math.max(MIN_DISTANCE, Math.hypot(a.getPx()-b.getPx(), a.getPy()-b.getPy()) / PX_PER_TILE);
  }

  /** Travel seconds between two islands for a group paced at {@code minutesPerTile}. */
  public int seconds(Long fromIslandId, Long toIslandId, int minutesPerTile){
    double dist = distanceTiles(fromIslandId, toIslandId);
    return (int) Math.max(5, Math.round(dist * Math.max(1, minutesPerTile) * SECONDS_PER_TILE_MINUTE));
  }

  /** Pace of the slowest unit in the group (largest minutes-per-tile); default if empty. */
  public int slowestMinutesPerTile(Map<String,Integer> units){
    int s = 0;
    for (var e : units.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      s = Math.max(s, catalog.get(e.getKey()).getSpeedMinutesPerTile());
    }
    return s == 0 ? DEFAULT_MINUTES_PER_TILE : s;
  }

  /** Name of the slowest unit type in the group, or null if empty. */
  public String slowestUnit(Map<String,Integer> units){
    String name = null; int s = -1;
    for (var e : units.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      int sp = catalog.get(e.getKey()).getSpeedMinutesPerTile();
      if (sp > s){ s = sp; name = catalog.get(e.getKey()).getName(); }
    }
    return name;
  }

  /** Full travel time between two cities for the given group of troops. */
  public Duration travelTime(Long originCityId, Long targetCityId, Map<String,Integer> units){
    City o = cities.findById(originCityId).orElseThrow(() -> new IllegalArgumentException("Origin city not found"));
    City t = cities.findById(targetCityId).orElseThrow(() -> new IllegalArgumentException("Target city not found"));
    return Duration.ofSeconds(seconds(o.getIslandId(), t.getIslandId(), slowestMinutesPerTile(units)));
  }
}
