package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * ESPIONAGE — building-based scouting via the Watchtower. A spy mission is a contest: the attacker's
 * Watchtower raises the success chance, the target's Watchtower raises the chance to catch. Exactly
 * two outcomes: SUCCESS (silent — full intel, defender NOT notified) or CAUGHT (no intel, defender
 * alerted with the spy's identity). Missions cost resources + a short delay and resolve in the tick.
 */
@Service
public class SpyService {
  private final CityRepo cities;
  private final CityService cityService;
  private final UnitRepo units;
  private final PlayerRepo players;
  private final SpyMissionRepo missions;
  private final SpyReportRepo reports;
  private final SpyAlertRepo alerts;
  private final Random rnd = new Random();

  public SpyService(CityRepo cities, CityService cityService, UnitRepo units, PlayerRepo players,
                    SpyMissionRepo missions, SpyReportRepo reports, SpyAlertRepo alerts){
    this.cities=cities; this.cityService=cityService; this.units=units; this.players=players;
    this.missions=missions; this.reports=reports; this.alerts=alerts;
  }

  /** Watchtower view for a city: its level and the two derived chances. */
  @Transactional(readOnly = true)
  public Map<String,Object> watchtower(Long playerId, Long cityId){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    int lv = cityService.level(cityId, BuildingType.WATCHTOWER);
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("cityId", cityId);
    m.put("level", lv);
    m.put("maxLevel", BuildingType.WATCHTOWER.max);
    m.put("spySuccessChance", Math.round(GameRules.spySuccessChance(lv) * 1000) / 10.0);   // %
    m.put("spyDefenseChance", Math.round(GameRules.spyDefenseChance(lv) * 1000) / 10.0);   // %
    m.put("cost", GameRules.SPY_RESOURCE_COST);
    m.put("seconds", GameRules.SPY_SECONDS);
    return m;
  }

  /** Launch a spy mission from one of the player's cities against a target city. */
  @Transactional
  public Map<String,Object> spy(Long playerId, Long originCityId, Long targetCityId){
    City origin = cities.findById(originCityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(origin.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    City target = cities.findById(targetCityId).orElseThrow(() -> new IllegalArgumentException("Target city not found"));
    if (Objects.equals(target.getPlayerId(), playerId)) throw new IllegalStateException("You can't spy your own city");
    if (cityService.level(originCityId, BuildingType.WATCHTOWER) < 1)
      throw new IllegalStateException("Build a Watchtower first to run spy missions");
    long cost = GameRules.SPY_RESOURCE_COST;
    if (origin.getWood() < cost || origin.getStone() < cost || origin.getWheat() < cost)
      throw new IllegalStateException("Not enough resources — a spy mission costs " + cost + " of each base resource");
    origin.setWood(origin.getWood() - cost); origin.setStone(origin.getStone() - cost); origin.setWheat(origin.getWheat() - cost);
    cities.save(origin);

    SpyMission m = new SpyMission();
    m.setSpyingPlayerId(playerId); m.setOriginCityId(originCityId); m.setTargetCityId(targetCityId);
    m.setResolvesAt(Instant.now().plusSeconds(GameRules.SPY_SECONDS));
    SpyMission saved = missions.save(m);

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("ok", true); out.put("status", "IN_PROGRESS");
    out.put("missionId", saved.getId());
    out.put("resolvesAt", saved.getResolvesAt().toString());
    return out;
  }

  /** Resolve all due spy missions (called from the scheduler each tick). */
  @Transactional
  public void resolveDue(Instant now){
    for (SpyMission m : missions.findByStatusAndResolvesAtLessThanEqual(SpyMission.Status.IN_PROGRESS, now)){
      resolveOne(m, now);
    }
  }

  private void resolveOne(SpyMission m, Instant now){
    City target = cities.findById(m.getTargetCityId()).orElse(null);
    int atkLv = cityService.level(m.getOriginCityId(), BuildingType.WATCHTOWER);
    int defLv = target == null ? 0 : cityService.level(target.getId(), BuildingType.WATCHTOWER);
    boolean success = target != null && rnd.nextDouble() < GameRules.effectiveSpyChance(atkLv, defLv);

    SpyReport report = new SpyReport();
    report.setOwnerPlayerId(m.getSpyingPlayerId());
    report.setTargetCityId(m.getTargetCityId());
    report.setTargetCityName(target == null ? "?" : target.getName());
    report.setCapturedAt(now);

    if (success){
      report.setOutcome(SpyOutcome.SUCCESS);
      report.setRevealedTroops(troopsOf(target.getId()));
      report.setRevealedResources(resourcesOf(target));
      report.setRevealedBuildings(buildingsOf(target.getId()));
      // silent: the defender is NOT notified
    } else {
      report.setOutcome(SpyOutcome.CAUGHT);
      // the defender learns who scouted them
      if (target != null && target.getPlayerId() != null){
        SpyAlert alert = new SpyAlert();
        alert.setOwnerPlayerId(target.getPlayerId());
        alert.setSpyingPlayerId(m.getSpyingPlayerId());
        alert.setSpyingPlayerName(players.findById(m.getSpyingPlayerId()).map(Player::getUsername).orElse("Unknown"));
        alert.setTargetCityId(target.getId());
        alert.setTargetCityName(target.getName());
        alert.setCaughtAt(now);
        alerts.save(alert);
      }
    }
    reports.save(report);
    m.setOutcome(report.getOutcome());
    m.setStatus(SpyMission.Status.RESOLVED);
    missions.save(m);
  }

  private Map<String,Integer> troopsOf(Long cityId){
    Map<String,Integer> t = new LinkedHashMap<>();
    for (CityUnit u : units.findByCityId(cityId)) if (u.getCount() > 0) t.merge(u.getType().toUpperCase(), u.getCount(), Integer::sum);
    return t;
  }

  private Map<String,Long> resourcesOf(City c){
    Race race = c.getRace() == null ? Race.HUMANS : c.getRace();
    Map<String,Long> r = new LinkedHashMap<>();
    r.put("WOOD", (long)c.getWood()); r.put("STONE", (long)c.getStone()); r.put("WHEAT", (long)c.getWheat());
    r.put(race.specialResource.name(), (long)c.get(race.specialResource));
    return r;
  }

  private Map<String,Integer> buildingsOf(Long cityId){
    Map<String,Integer> b = new LinkedHashMap<>();
    for (var e : cityService.levels(cityId).entrySet()) b.put(e.getKey().name(), e.getValue());
    return b;
  }

  // ---- read views ----

  @Transactional(readOnly = true)
  public List<Map<String,Object>> myReports(Long playerId){
    List<Map<String,Object>> out = new ArrayList<>();
    for (SpyReport r : reports.findByOwnerPlayerIdOrderByCapturedAtDesc(playerId)){
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("id", r.getId()); m.put("targetCityId", r.getTargetCityId()); m.put("targetCityName", r.getTargetCityName());
      m.put("outcome", r.getOutcome().name()); m.put("capturedAt", r.getCapturedAt().toString());
      m.put("troops", r.getRevealedTroops()); m.put("resources", r.getRevealedResources()); m.put("buildings", r.getRevealedBuildings());
      out.add(m);
    }
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String,Object>> myAlerts(Long playerId){
    List<Map<String,Object>> out = new ArrayList<>();
    for (SpyAlert a : alerts.findByOwnerPlayerIdOrderByCaughtAtDesc(playerId)){
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("id", a.getId()); m.put("spyingPlayerName", a.getSpyingPlayerName());
      m.put("targetCityName", a.getTargetCityName()); m.put("caughtAt", a.getCaughtAt().toString());
      out.add(m);
    }
    return out;
  }

  /** Most recent SUCCESS report on a target (for the attack-flow intel hint), or null. */
  @Transactional(readOnly = true)
  public Map<String,Object> latestIntel(Long playerId, Long targetCityId){
    return reports.findByOwnerPlayerIdOrderByCapturedAtDesc(playerId).stream()
        .filter(r -> Objects.equals(r.getTargetCityId(), targetCityId) && r.getOutcome() == SpyOutcome.SUCCESS)
        .findFirst()
        .map(r -> { Map<String,Object> m = new LinkedHashMap<>();
          m.put("capturedAt", r.getCapturedAt().toString()); m.put("troops", r.getRevealedTroops());
          m.put("resources", r.getRevealedResources()); m.put("buildings", r.getRevealedBuildings()); return m; })
        .orElse(null);
  }
}
