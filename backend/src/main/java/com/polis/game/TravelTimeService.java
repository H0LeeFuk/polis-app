package com.polis.game;

import com.polis.domain.City;
import com.polis.domain.Island;
import com.polis.domain.MovementClass;
import com.polis.domain.Race;
import com.polis.domain.UnitType;
import com.polis.repo.CityRepo;
import com.polis.repo.IslandRepo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

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

  /** Full travel time between two cities for the given group of troops (terrain-aware). */
  public Duration travelTime(Long originCityId, Long targetCityId, Map<String,Integer> units){
    City o = cities.findById(originCityId).orElseThrow(() -> new IllegalArgumentException("Origin city not found"));
    City t = cities.findById(targetCityId).orElseThrow(() -> new IllegalArgumentException("Target city not found"));
    boolean water = crossesWater(o.getIslandId(), t.getIslandId());
    return Duration.ofSeconds(GameRules.fast(seconds(o.getIslandId(), t.getIslandId(), effectiveMinutesPerTile(units, water))));
  }

  // --- movement class: water crossing + terrain-aware pace + transport capacity --------------

  /** Penalty applied to a SWIMMING unit's pace while moving over land (higher = slower). */
  public static final double LAND_PENALTY = 1.8;

  /** A route spans open water whenever it links two different islands. */
  public boolean crossesWater(Long fromIslandId, Long toIslandId){
    return !Objects.equals(fromIslandId, toIslandId);
  }

  /** Total population of the LAND units in a group (the load that needs ferrying across water). */
  public int landPopulation(Map<String,Integer> units){
    int p = 0;
    for (var e : units.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      UnitType u = catalog.get(e.getKey());
      if (u.getMovementClass()==MovementClass.LAND) p += u.getPopulationCost()*e.getValue();
    }
    return p;
  }

  /** Total transport capacity (population of LAND troops carriable) provided by ships in the group. */
  public int transportCapacityProvided(Map<String,Integer> units){
    int c = 0;
    for (var e : units.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      c += catalog.get(e.getKey()).getTransportCapacity()*e.getValue();
    }
    return c;
  }

  /** Largest single-ship capacity present — a unit can only board a ship at least as big as its pop. */
  public int largestTransportCapacity(Map<String,Integer> units){
    int max = 0;
    for (var e : units.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      max = Math.max(max, catalog.get(e.getKey()).getTransportCapacity());
    }
    return max;
  }

  /** Population of the single heaviest LAND unit in the group (must fit aboard one ship). */
  public int heaviestLandUnitPop(Map<String,Integer> units){
    int max = 0;
    for (var e : units.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      UnitType u = catalog.get(e.getKey());
      if (u.getMovementClass()==MovementClass.LAND) max = Math.max(max, u.getPopulationCost());
    }
    return max;
  }

  /**
   * Terrain-aware pace of the slowest member of the group (largest minutes-per-tile).
   * On a water route LAND units are embarked and move at the slowest transport's sea speed;
   * on a land route SWIMMING units crawl at {@link #LAND_PENALTY}× their pace.
   */
  public int effectiveMinutesPerTile(Map<String,Integer> units, boolean crossesWater){
    int transportPace = 0;   // slowest transport (sea speed the embarked land troops inherit)
    for (var e : units.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      UnitType u = catalog.get(e.getKey());
      if (u.getTransportCapacity()>0) transportPace = Math.max(transportPace, u.getSpeedMinutesPerTile());
    }
    int slowest = 0;
    for (var e : units.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      UnitType u = catalog.get(e.getKey());
      int pace;
      if (crossesWater){
        pace = u.getMovementClass()==MovementClass.LAND
            ? (transportPace>0 ? transportPace : u.getSpeedMinutesPerTile())   // embarked
            : u.getSpeedMinutesPerTile();
      } else {
        pace = u.getMovementClass()==MovementClass.SWIMMING
            ? (int)Math.round(u.getSpeedMinutesPerTile()*LAND_PENALTY)
            : u.getSpeedMinutesPerTile();
      }
      slowest = Math.max(slowest, pace);
    }
    return slowest==0 ? DEFAULT_MINUTES_PER_TILE : slowest;
  }

  /** Transport sufficiency for a water crossing. ok==true when the group can make the trip. */
  public record TransportCheck(boolean crossesWater, int requiredCapacity, int providedCapacity,
                               boolean sufficient, int shipsShort, String reason){}

  /** Approximate per-ship capacity used to suggest how many more transports are needed. */
  private static final int TYPICAL_SHIP_CAPACITY = 30;

  /** Transport load of a marching hero: land heroes need ferrying, flyers/swimmers do not. */
  public static final int HERO_LAND_POP = 10;
  /** A hero that does not fly (Fairy) or swim (Newt) is a land traveller and needs a boat. */
  public int heroLandLoad(Race heroRace){ return (heroRace==Race.FAIRIES || heroRace==Race.NEWTS) ? 0 : HERO_LAND_POP; }

  public TransportCheck checkTransport(Map<String,Integer> units, boolean crossesWater){
    return checkTransport(units, crossesWater, 0);
  }

  /**
   * Evaluate whether a group's LAND troops (plus any land hero, via {@code extraLandPop}) can cross
   * water with the transports included.
   */
  public TransportCheck checkTransport(Map<String,Integer> units, boolean crossesWater, int extraLandPop){
    int provided = transportCapacityProvided(units);
    // still report the ships' carrying capacity on a land route so the dispatch UI can show it
    // (e.g. a Galley selected for a same-island target reads "0 / 30") — no crossing required.
    if (!crossesWater) return new TransportCheck(false, 0, provided, true, 0, null);
    int required = landPopulation(units) + Math.max(0, extraLandPop);
    if (required <= 0) return new TransportCheck(true, 0, provided, true, 0, null);   // flyers/swimmers only
    // total capacity shortfall (covers the common "no ships at all" case) — report first
    if (provided < required){
      int shipsShort = (int)Math.ceil((required - provided) / (double)TYPICAL_SHIP_CAPACITY);
      return new TransportCheck(true, required, provided, false, Math.max(1, shipsShort),
          "Not enough transport capacity to ferry your land forces across water "
              + "(need " + required + ", have " + provided + ")");
    }
    // enough total capacity, but a single passenger may be too heavy for any one ship aboard
    int heaviest = Math.max(heaviestLandUnitPop(units), Math.max(0, extraLandPop));
    int biggestShip = largestTransportCapacity(units);
    if (biggestShip < heaviest)
      return new TransportCheck(true, required, provided, false, 0,
          "Your heaviest land passenger (" + heaviest + " pop) needs a bigger transport to board");
    return new TransportCheck(true, required, provided, true, 0, null);
  }

  /** Throw a clear error if a water-crossing group of LAND troops lacks sufficient transport. */
  public void requireTransport(Map<String,Integer> units, Long fromIslandId, Long toIslandId){
    requireTransport(units, fromIslandId, toIslandId, 0);
  }

  /** As above, including a land hero's transport load ({@code extraLandPop}). */
  public void requireTransport(Map<String,Integer> units, Long fromIslandId, Long toIslandId, int extraLandPop){
    TransportCheck c = checkTransport(units, crossesWater(fromIslandId, toIslandId), extraLandPop);
    if (!c.sufficient()) throw new IllegalStateException(c.reason());
  }
}
