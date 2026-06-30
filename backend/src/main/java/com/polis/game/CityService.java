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
  private final MovementRepo movements;
  private final PlayerRepo players;

  /** Dev account that keeps resources pinned at the warehouse cap (never depletes). */
  private static final String GOD_ACCOUNT = "bruno";

  public CityService(CityRepo cities, BuildingRepo buildings, UnitRepo units, JobRepo jobs,
                     UnitCatalog catalog, MissionService missions, LibraryService library,
                     MovementRepo movements, PlayerRepo players){
    this.cities=cities; this.buildings=buildings; this.units=units; this.jobs=jobs; this.catalog=catalog;
    this.missions=missions; this.library=library; this.movements=movements; this.players=players;
  }

  public Map<BuildingType,Integer> levels(Long cityId){
    Map<BuildingType,Integer> m = new EnumMap<>(BuildingType.class);
    for (BuildingType t : BuildingType.values()) m.put(t, 0);
    for (CityBuilding b : buildings.findByCityId(cityId)) m.put(b.getType(), b.getLevel());
    return m;
  }

  public int level(Long cityId, BuildingType t){ return levels(cityId).getOrDefault(t,0); }

  public long capacity(Long cityId){
    // Warehouse cap, raised by Library storage research (War Stores / Deep Vaults / Burrow Stores).
    return Math.round(GameRules.storeCap(level(cityId, BuildingType.WAREHOUSE)) * library.effects(cityId).storageMult());
  }

  /** True for the dev "god" account whose resources stay pinned at the warehouse cap. */
  private boolean isGodAccount(Long playerId){
    return players.findById(playerId).map(p -> GOD_ACCOUNT.equalsIgnoreCase(p.getUsername())).orElse(false);
  }

  public int maxPop(Long cityId, boolean bounty){
    int p = GameRules.farmPop(level(cityId, BuildingType.FARM));
    return bounty ? (int)Math.round(p*1.15) : p;
  }

  public int popUsed(Long cityId){
    int used = 0;
    for (CityBuilding b : buildings.findByCityId(cityId)) used += b.getLevel()*b.getType().pop;
    for (CityUnit u : units.findByCityId(cityId)) used += u.getCount()*catalog.get(u.getType()).getPopulationCost();
    // units still in the training queue already reserve their population — otherwise the city looks
    // like it has free pop while a batch is brewing, and a second train order would over-fill the cap.
    for (BuildJob j : jobs.findByCityIdOrderByQueueTypeAscPositionAsc(cityId))
      if (j.getUnitType() != null && j.getBatch() != null)
        used += j.getBatch() * catalog.get(j.getUnitType()).getPopulationCost();
    // troops marching from this city still belong to it — count them so population is steady while
    // an army is away (it only changes on recruit, upgrade, or losses), not when you send an attack.
    for (Movement m : movements.findBySourceCityIdAndResolvedFalse(cityId))
      if (m.getUnits() != null)
        for (var e : m.getUnits().entrySet())
          if (e.getValue() != null && e.getValue() > 0)
            used += e.getValue() * catalog.get(e.getKey()).getPopulationCost();
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
      LibraryService.LibEffects fx = library.effects(c.getId());
      long cap = Math.round(GameRules.storeCap(lv.get(BuildingType.WAREHOUSE)) * fx.storageMult());
      // production: race bonus → Library production research (documented stacking order)
      double prod = (c.getRace()!=null ? c.getRace().prodMult : 1.0) * fx.prodMult();
      c.setWood(Math.min(cap,  c.getWood()  + GameRules.prodPerHour(lv.get(BuildingType.TIMBER))*prod/3600.0*elapsed));
      c.setStone(Math.min(cap, c.getStone() + GameRules.prodPerHour(lv.get(BuildingType.QUARRY))*prod/3600.0*elapsed));
      c.setWheat(Math.min(cap, c.getWheat() + GameRules.prodPerHour(lv.get(BuildingType.MINE))*prod/3600.0*elapsed));
      // special resource: the EXTRACTOR yields the city RACE's special (Coal/Iron/Crystals/Pearls);
      // the Wild Hunt "Deep Veins" research further boosts ONLY the special resource.
      int extractor = lv.get(BuildingType.EXTRACTOR);
      if (extractor>0 && c.getRace()!=null){
        ResourceType sp = c.getRace().specialResource;
        c.set(sp, Math.min(cap, c.get(sp) + GameRules.prodPerHour(extractor)*prod*fx.specialProdMult()/3600.0*elapsed));
      }
      c.setLastTickAt(now);
    }
    // GOD ACCOUNT: the dev account's cities stay pinned exactly at the warehouse cap —
    // resources never deplete and never exceed the cap. Every other account obeys the
    // normal capped accrual above.
    if (c.getPlayerId()!=null && isGodAccount(c.getPlayerId())){
      double cap = capacity(c.getId());
      c.setWood(cap);
      c.setStone(cap);
      c.setWheat(cap);
      if (c.getRace()!=null) c.set(c.getRace().specialResource, cap);
    }
    if (c.getPlayerId()!=null) c.setPoints(GameRules.cityPoints(levels(c.getId())));
    return cities.save(c);
  }

  /** Completes any active job whose finish time has passed, promoting the next in queue. */
  @Transactional
  public void finalizeJobs(City c, Instant now){
    // Take a write lock on the city row so only one finalizer (rush / sync / tick) runs
    // this critical section per city at a time. The job lists below are then re-queried
    // inside the lock and reflect any prior finalizer's committed deletes/promotions —
    // preventing the double-promote that left a phantom "extra" build job.
    cities.findByIdForUpdate(c.getId());
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
      // INVARIANT NORMALIZATION (self-heals any corruption from any path): positions are contiguous
      // 0..n and ONLY the head (position 0) is running. This runs on every read/sync under the city
      // lock, so a queue can never display two "running" slots or out-of-order numbers for long.
      normalizeQueue(q, now);
    }
  }

  /** Enforce: contiguous positions 0..n, exactly the head running (finishAt set), all others idle. */
  private void normalizeQueue(List<BuildJob> q, Instant now){
    boolean dirty = false;
    for (int i = 0; i < q.size(); i++){
      BuildJob b = q.get(i);
      if (b.getPosition() != i){ b.setPosition(i); dirty = true; }
      if (i == 0){
        if (b.getFinishAt() == null){ b.setStartedAt(now); b.setFinishAt(now.plusSeconds(b.getTotalSeconds())); dirty = true; }
      } else if (b.getFinishAt() != null || b.getStartedAt() != null){
        b.setFinishAt(null); b.setStartedAt(null); dirty = true;   // only the head may run
      }
    }
    if (dirty) jobs.saveAll(q);
  }

  /**
   * Complete one job immediately (gold rush), even mid-queue: apply its effect, drop it, and
   * re-sequence the rest. Used when a queued job has no same-building predecessor blocking it.
   */
  @Transactional
  public void forceComplete(City c, BuildJob j){
    cities.findByIdForUpdate(c.getId());
    applyJob(c, j);
    QueueType qt = j.getQueueType();
    jobs.delete(j);
    jobs.flush();   // force the DELETE to hit the DB before re-reading the queue
    // Re-read and DROP the just-deleted job from the list (mirrors finalizeJobs). Re-saving a
    // re-queried row that still includes j would cancel the pending delete in the persistence
    // context — leaving a phantom job that never disappears and inflates the queue by a slot.
    List<BuildJob> q = jobs.findByCityIdAndQueueTypeOrderByPositionAsc(c.getId(), qt);
    q.removeIf(b -> Objects.equals(b.getId(), j.getId()));
    Instant now = Instant.now();
    for (int i = 0; i < q.size(); i++){
      BuildJob b = q.get(i); b.setPosition(i);
      if (i == 0 && b.getFinishAt() == null){ b.setStartedAt(now); b.setFinishAt(now.plusSeconds(b.getTotalSeconds())); }
    }
    jobs.saveAll(q);
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
      if (j.getBuildingType()==BuildingType.LIBRARY)
        missions.record(pid, MissionObjectiveType.REACH_LIBRARY_LEVEL, j.getToLevel());
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
