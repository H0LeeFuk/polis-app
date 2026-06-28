package com.polis.game;

import com.polis.domain.City;
import com.polis.domain.Island;
import com.polis.domain.UnitType;
import com.polis.repo.CityRepo;
import com.polis.repo.IslandRepo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Single source of truth for how long troops take to travel between two cities.
 * Distance is the world-map pixel distance between the two islands; the journey
 * is paced by the SLOWEST unit in the group (a catapult drags the army down).
 */
@Service
public class TravelTimeService {
  /** Pixels per map tile — the world canvas is laid out in raw pixels. */
  private static final double PX_PER_TILE = 12.0;
  /** Minutes-per-tile baseline; an 18-speed unit walks 1 tile per (18/speed) of this. */
  private static final double SPEED_BASE = 18.0;
  /** Floor so same-island raids still take a sensible march. */
  private static final double MIN_DISTANCE = 8.0;

  private final IslandRepo islands;
  private final CityRepo cities;

  public TravelTimeService(IslandRepo islands, CityRepo cities){
    this.islands = islands; this.cities = cities;
  }

  /** Distance in tiles between two islands (floored to MIN_DISTANCE). */
  public double distanceTiles(Long fromIslandId, Long toIslandId){
    Island a = islands.findById(fromIslandId).orElseThrow();
    Island b = islands.findById(toIslandId).orElseThrow();
    return Math.max(MIN_DISTANCE, Math.hypot(a.getPx()-b.getPx(), a.getPy()-b.getPy()) / PX_PER_TILE);
  }

  /** Travel seconds between two islands for a group moving at {@code speed}. */
  public int seconds(Long fromIslandId, Long toIslandId, int speed){
    double dist = distanceTiles(fromIslandId, toIslandId);
    return (int) Math.round(dist * (SPEED_BASE / Math.max(1, speed)));
  }

  /** Speed of the slowest unit in the group; 15 if the group is empty. */
  public int slowestSpeed(Map<String,Integer> units){
    int s = Integer.MAX_VALUE;
    for (var e : units.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      s = Math.min(s, UnitType.valueOf(e.getKey().toUpperCase()).speed);
    }
    return s == Integer.MAX_VALUE ? 15 : s;
  }

  /** Name of the slowest unit type in the group, or null if empty. */
  public String slowestUnit(Map<String,Integer> units){
    String name = null; int s = Integer.MAX_VALUE;
    for (var e : units.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      int sp = UnitType.valueOf(e.getKey().toUpperCase()).speed;
      if (sp < s){ s = sp; name = e.getKey().toUpperCase(); }
    }
    return name;
  }

  /** Full travel time between two cities for the given group of troops. */
  public Duration travelTime(Long originCityId, Long targetCityId, Map<String,Integer> units){
    City o = cities.findById(originCityId).orElseThrow(() -> new IllegalArgumentException("Origin city not found"));
    City t = cities.findById(targetCityId).orElseThrow(() -> new IllegalArgumentException("Target city not found"));
    return Duration.ofSeconds(seconds(o.getIslandId(), t.getIslandId(), slowestSpeed(units)));
  }
}
