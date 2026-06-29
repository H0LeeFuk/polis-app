package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * The Temple hosts Festivals. A festival burns one of two fuels (resources OR Combat Points) and,
 * on completion, grants account-wide Culture Points (→ levels → city slots). Completed by the
 * scheduled resolver. Festivals are run from a city but progress the player, not the city.
 */
@Service
public class TempleService {
  private final CityRepo cities;
  private final CityService cityService;
  private final PlayerRepo players;
  private final FestivalRepo festivals;
  private final ProgressionService progression;

  public TempleService(CityRepo cities, CityService cityService, PlayerRepo players,
                       FestivalRepo festivals, ProgressionService progression){
    this.cities = cities; this.cityService = cityService; this.players = players;
    this.festivals = festivals; this.progression = progression;
  }

  private City owned(Long playerId, Long cityId){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    return cityService.sync(c);
  }

  @Transactional
  public Festival start(Long playerId, Long cityId, Festival.Type type, Festival.Fuel fuel){
    City c = owned(playerId, cityId);
    if (cityService.level(cityId, BuildingType.TEMPLE) <= 0)
      throw new IllegalStateException("Build a Temple first");
    // Both festival types may run together (one Plenty + one Triumph); only block a second of the SAME type.
    if (festivals.countByCityIdAndFestivalTypeAndStatus(cityId, type, Festival.Status.RUNNING) > 0)
      throw new IllegalStateException("That festival is already running in this city");
    // the two options are fixed pairings of festival + fuel
    Festival.Fuel expected = type == Festival.Type.FESTIVAL_OF_PLENTY ? Festival.Fuel.RESOURCES : Festival.Fuel.COMBAT_POINTS;
    if (fuel != expected) throw new IllegalArgumentException("That festival uses " + expected + " as fuel");

    Player p = players.findById(playerId).orElseThrow();
    if (fuel == Festival.Fuel.RESOURCES){
      long cost = GameRules.FESTIVAL_RESOURCE_COST;
      if (c.getWood() < cost || c.getStone() < cost || c.getWheat() < cost)
        throw new IllegalStateException("Not enough resources — need " + cost + " of each (Wood, Stone, Wheat)");
      c.setWood(c.getWood() - cost); c.setStone(c.getStone() - cost); c.setWheat(c.getWheat() - cost);
      cities.save(c);
    } else {
      if (p.getCombatPoints() < GameRules.FESTIVAL_COMBAT_COST)
        throw new IllegalStateException("Not enough Combat Points — need " + GameRules.FESTIVAL_COMBAT_COST);
      p.setCombatPoints(p.getCombatPoints() - GameRules.FESTIVAL_COMBAT_COST); players.save(p);
    }

    Instant now = Instant.now();
    Festival f = new Festival();
    f.setCityId(cityId); f.setPlayerId(playerId);
    f.setFestivalType(type); f.setFuelType(fuel); f.setStatus(Festival.Status.RUNNING);
    f.setStartedAt(now); f.setCompletesAt(now.plusSeconds(GameRules.FESTIVAL_SECONDS));
    f.setCulturePointsReward(GameRules.FESTIVAL_CULTURE_REWARD);
    return festivals.save(f);
  }

  /** Scheduled resolver: complete due festivals, granting Culture (and any level-ups). */
  @Transactional
  public void completeDue(Instant now){
    for (Festival f : festivals.findByStatusAndCompletesAtLessThanEqual(Festival.Status.RUNNING, now)){
      f.setStatus(Festival.Status.COMPLETED);
      festivals.save(f);
      progression.grantCulture(f.getPlayerId(), f.getCulturePointsReward());
    }
  }

  @Transactional
  public Map<String,Object> templeState(Long playerId, Long cityId){
    City c = owned(playerId, cityId);
    int level = cityService.level(cityId, BuildingType.TEMPLE);
    Player p = players.findById(playerId).orElseThrow();
    List<Festival> running = festivals.findByCityIdAndStatusOrderByStartedAtDesc(cityId, Festival.Status.RUNNING);

    Map<String,Object> m = new LinkedHashMap<>();
    m.put("templeLevel", level);
    m.put("combatPoints", p.getCombatPoints());
    m.put("resourceCost", GameRules.FESTIVAL_RESOURCE_COST);
    m.put("combatCost", GameRules.FESTIVAL_COMBAT_COST);
    m.put("cultureReward", GameRules.FESTIVAL_CULTURE_REWARD);
    m.put("durationSeconds", GameRules.FESTIVAL_SECONDS);
    // affordability of each option from this city / account
    m.put("canAffordResources", c.getWood() >= GameRules.FESTIVAL_RESOURCE_COST
        && c.getStone() >= GameRules.FESTIVAL_RESOURCE_COST && c.getWheat() >= GameRules.FESTIVAL_RESOURCE_COST);
    m.put("canAffordCombat", p.getCombatPoints() >= GameRules.FESTIVAL_COMBAT_COST);
    // both festival types can run at once → return the full set of running festivals
    List<Map<String,Object>> runningList = new ArrayList<>();
    for (Festival f : running){
      Map<String,Object> r = new LinkedHashMap<>();
      r.put("festivalType", f.getFestivalType().name());
      r.put("fuelType", f.getFuelType().name());
      r.put("completesAt", f.getCompletesAt().toString());
      r.put("culturePointsReward", f.getCulturePointsReward());
      runningList.add(r);
    }
    m.put("running", runningList);
    m.put("progression", progression.progression(playerId));
    return m;
  }
}
