package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Owns the authoritative per-city simulation: lazy resource accrual + finalizing
 * any build/training jobs whose timers have elapsed. Called on every read and write
 * so a city is always brought up to date with real wall-clock time first.
 */
@Service
public class CityService {

  private final CityRepo cities;
  private final BuildingRepo buildings;
  private final UnitRepo units;
  private final JobRepo jobs;
  private final UnitCatalog catalog;
  private final MissionService missions;
  private final LibraryService library;

  public CityService(CityRepo cities, BuildingRepo buildings, UnitRepo units, JobRepo jobs,
                     UnitCatalog catalog, MissionService missions, LibraryService library){
    this.cities=cities; this.buildings=buildings; this.units=units; this.jobs=jobs; this.catalog=catalog;
    this.missions=missions; this.library=library;
  }

  public Map<BuildingType,Integer> levels(Long cityId){
    Map<BuildingType,Integer> m = new EnumMap<>(BuildingType.class);
    for (BuildingType t : BuildingType.values()) m.put(t, 0);
    for (CityBuilding b : buildings.findByCityId(cityId)) m.put(b.getType(), b.getLevel());
    return m;
  }

  public int level(Long cityId, BuildingType t){ return levels(cityId).getOrDefault(t,0); }

  public long capacity(Long cityId){ return GameRules.storeCap(level(cityId, BuildingType.WAREHOUSE)); }

  public int maxPop(Long cityId, boolean bounty){
    int p = GameRules.farmPop(level(cityId, BuildingType.FARM));
    return bounty ? (int)Math.round(p*1.15) : p;
  }

  public int popUsed(Long cityId){
    int used = 0;
    for (CityBuilding b : buildings.findByCityId(cityId)) used += b.getLevel()*b.getType().pop;
    for (CityUnit u : units.findByCityId(cityId)) used += u.getCount()*catalog.get(u.getType()).getPopulationCost();
    return used;
  }

  /** Brings the city to "now": accrues resources (capped) and finalizes completed jobs. */
  @Transactional
  public City sync(City c){
    Instant now = Instant.now();
    finalizeJobs(c, now);

    double elapsed = Math.max(0, (now.toEpochMilli() - c.getLastTickAt().toEpochMilli())/1000.0);
    if (elapsed > 0){
      Map<BuildingType,Integer> lv = levels(c.getId());
      long cap = GameRules.storeCap(lv.get(BuildingType.WAREHOUSE));
      // production: race bonus → Library "Abundance" research (documented stacking order)
      double prod = (c.getRace()!=null ? c.getRace().prodMult : 1.0) * library.effects(c.getId()).prodMult();
      c.setWood(Math.min(cap,  c.getWood()  + GameRules.prodPerHour(lv.get(BuildingType.TIMBER))*prod/3600.0*elapsed));
      c.setStone(Math.min(cap, c.getStone() + GameRules.prodPerHour(lv.get(BuildingType.QUARRY))*prod/3600.0*elapsed));
      c.setSilver(Math.min(cap,c.getSilver()+ GameRules.prodPerHour(lv.get(BuildingType.MINE))*prod/3600.0*elapsed));
      int temple = lv.get(BuildingType.TEMPLE);
      if (temple>0)
        c.setFavor(Math.min(GameRules.favorCap(temple), c.getFavor()+GameRules.favorPerHour(temple)/3600.0*elapsed));
      c.setLastTickAt(now);
    }
    // TEST MODE: keep player cities topped up with resources for free testing.
    if (c.getPlayerId()!=null){
      double TEST = 1_000_000_000d;
      if (c.getWood()<TEST)   c.setWood(TEST);
      if (c.getStone()<TEST)  c.setStone(TEST);
      if (c.getSilver()<TEST) c.setSilver(TEST);
    }
    if (c.getPlayerId()!=null) c.setPoints(GameRules.cityPoints(levels(c.getId())));
    return cities.save(c);
  }

  /** Completes any active job whose finish time has passed, promoting the next in queue. */
  @Transactional
  public void finalizeJobs(City c, Instant now){
    for (QueueType qt : QueueType.values()){
      List<BuildJob> q = jobs.findByCityIdAndQueueTypeOrderByPositionAsc(c.getId(), qt);
      boolean changed = true;
      while (changed && !q.isEmpty()){
        changed = false;
        BuildJob head = q.get(0);
        if (head.getFinishAt()!=null && !head.getFinishAt().isAfter(now)){
          applyJob(c, head);
          jobs.delete(head);
          q.remove(0);
          // promote the rest
          for (int i=0;i<q.size();i++) q.get(i).setPosition(i);
          if (!q.isEmpty()){
            BuildJob next = q.get(0);
            next.setStartedAt(head.getFinishAt());
            next.setFinishAt(head.getFinishAt().plusSeconds(next.getTotalSeconds()));
          }
          jobs.saveAll(q);
          changed = true;
        }
      }
    }
  }

  private void applyJob(City c, BuildJob j){
    if (j.getQueueType()==QueueType.BUILDING){
      CityBuilding b = buildings.findByCityId(c.getId()).stream()
        .filter(x->x.getType()==j.getBuildingType()).findFirst()
        .orElseGet(()->new CityBuilding(c.getId(), j.getBuildingType(), 0));
      b.setLevel(j.getToLevel());
      buildings.save(b);
      // mission progress: a building was completed / upgraded
      Long pid = c.getPlayerId();
      missions.record(pid, MissionObjectiveType.BUILD_BUILDING, 1);
      missions.record(pid, MissionObjectiveType.UPGRADE_BUILDING_LEVEL, j.getToLevel());
      if (j.getBuildingType()==BuildingType.ACADEMY)
        missions.record(pid, MissionObjectiveType.REACH_ACADEMY_LEVEL, j.getToLevel());
    } else {
      CityUnit u = units.findByCityId(c.getId()).stream()
        .filter(x->x.getType().equals(j.getUnitType())).findFirst()
        .orElseGet(()->new CityUnit(c.getId(), j.getUnitType(), 0));
      u.setCount(u.getCount()+j.getBatch());
      units.save(u);
      missions.record(c.getPlayerId(), MissionObjectiveType.TRAIN_TROOPS, j.getBatch());
    }
  }
}
