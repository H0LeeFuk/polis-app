package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Validates and applies player orders. All spend/queue logic is server-authoritative. */
@Service
public class BuildService {

  private static final int BUILD_QUEUE_MAX = 5;
  private static final int UNIT_QUEUE_MAX  = 8;
  private static final long[] COLONY_COST  = {2000, 2000, 1500};

  private final CityService cityService;
  private final CityRepo cities;
  private final BuildingRepo buildings;
  private final UnitRepo units;
  private final ResearchRepo research;
  private final JobRepo jobs;
  private final MovementRepo movements;
  private final PlayerRepo players;
  private final TravelTimeService travel;

  public BuildService(CityService cityService, CityRepo cities, BuildingRepo buildings, UnitRepo units,
                      ResearchRepo research, JobRepo jobs, MovementRepo movements, PlayerRepo players,
                      TravelTimeService travel){
    this.cityService=cityService; this.cities=cities; this.buildings=buildings; this.units=units;
    this.research=research; this.jobs=jobs; this.movements=movements; this.players=players;
    this.travel=travel;
  }

  private City owned(Long playerId, Long cityId){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    return cityService.sync(c);
  }
  private boolean afford(City c, long w, long s, long si){ return c.getWood()>=w && c.getStone()>=s && c.getSilver()>=si; }
  private void pay(City c, long w, long s, long si){ c.setWood(c.getWood()-w); c.setStone(c.getStone()-s); c.setSilver(c.getSilver()-si); }

  private void enqueue(City c, BuildJob job, QueueType qt, int totalSeconds){
    int pos = (int) jobs.countByCityIdAndQueueType(c.getId(), qt);
    job.setCityId(c.getId()); job.setQueueType(qt); job.setPosition(pos); job.setTotalSeconds(totalSeconds);
    if (pos == 0){ Instant now=Instant.now(); job.setStartedAt(now); job.setFinishAt(now.plusSeconds(totalSeconds)); }
    jobs.save(job);
  }

  @Transactional
  public void build(Long playerId, Long cityId, BuildingType type){
    City c = owned(playerId, cityId);
    if (jobs.countByCityIdAndQueueType(cityId, QueueType.BUILDING) >= BUILD_QUEUE_MAX)
      throw new IllegalStateException("Construction queue is full (max " + BUILD_QUEUE_MAX + ")");
    // Effective level = built level + upgrades of this building already queued, so
    // stacking multiple upgrades targets the right next level each time.
    int queuedSame = (int) jobs.findByCityIdAndQueueTypeOrderByPositionAsc(cityId, QueueType.BUILDING)
        .stream().filter(j -> j.getBuildingType()==type).count();
    int lv = cityService.level(cityId, type) + queuedSame;
    if (lv >= type.max) throw new IllegalStateException(type + " is already at max level");
    long[] cost = GameRules.buildCost(type, lv);
    if (!afford(c, cost[0], cost[1], cost[2])) throw new IllegalStateException("Not enough resources");
    // type.pop == 0 (e.g. FARM) needs no population — and must never be blocked, else
    // an over-populated city could never upgrade the Farm to raise its own pop cap.
    if (type.pop > 0 && cityService.maxPop(cityId, hasResearch(cityId, ResearchType.BOUNTY)) - cityService.popUsed(cityId) < type.pop)
      throw new IllegalStateException("Not enough free population — upgrade the Farm");
    pay(c, cost[0], cost[1], cost[2]); cities.save(c);
    int secs = GameRules.buildSeconds(type, lv, cityService.level(cityId, BuildingType.SENATE));
    BuildJob job = new BuildJob(); job.setBuildingType(type); job.setToLevel(lv+1);
    enqueue(c, job, QueueType.BUILDING, secs);
  }

  @Transactional
  public void train(Long playerId, Long cityId, UnitType type, int count){
    City c = owned(playerId, cityId);
    if (count <= 0) throw new IllegalArgumentException("Count must be positive");
    if (type.research != null && !hasResearch(cityId, type.research))
      throw new IllegalStateException("Requires research: " + type.research);
    int fromLevel = cityService.level(cityId, type.from==QueueType.HARBOR ? BuildingType.HARBOR : BuildingType.BARRACKS);
    if (fromLevel <= 0) throw new IllegalStateException("Build the " + type.from + " first");
    if (jobs.countByCityIdAndQueueType(cityId, type.from) >= UNIT_QUEUE_MAX)
      throw new IllegalStateException("Training queue is full");
    int freePop = cityService.maxPop(cityId, hasResearch(cityId, ResearchType.BOUNTY)) - cityService.popUsed(cityId);
    count = Math.min(count, freePop / Math.max(1,type.pop));
    int byRes = (int) Math.min(Math.min(
        type.costWood >0 ? (long)(c.getWood()/type.costWood)   : Long.MAX_VALUE,
        type.costStone>0 ? (long)(c.getStone()/type.costStone) : Long.MAX_VALUE),
        type.costSilver>0? (long)(c.getSilver()/type.costSilver): Long.MAX_VALUE);
    count = Math.min(count, byRes);
    if (count <= 0) throw new IllegalStateException("Not enough population or resources");
    pay(c, (long)type.costWood*count, (long)type.costStone*count, (long)type.costSilver*count); cities.save(c);
    int total = GameRules.unitSeconds(type, fromLevel) * count;
    BuildJob job = new BuildJob(); job.setUnitType(type); job.setBatch(count);
    enqueue(c, job, type.from, total);
  }

  @Transactional
  public void research(Long playerId, Long cityId, ResearchType type){
    City c = owned(playerId, cityId);
    if (cityService.level(cityId, BuildingType.ACADEMY) < type.req)
      throw new IllegalStateException("Academy level too low (needs " + type.req + ")");
    if (hasResearch(cityId, type)) throw new IllegalStateException("Already researched");
    if (!afford(c, type.costWood, type.costStone, type.costSilver)) throw new IllegalStateException("Not enough resources");
    pay(c, type.costWood, type.costStone, type.costSilver); cities.save(c);
    research.save(new CityResearch(cityId, type));
  }

  @Transactional
  public void cancel(Long playerId, Long cityId, Long jobId){
    owned(playerId, cityId);
    BuildJob j = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
    if (!Objects.equals(j.getCityId(), cityId)) throw new IllegalStateException("Wrong city");
    City c = cities.findById(cityId).orElseThrow();
    if (j.getQueueType()==QueueType.BUILDING){
      long[] r = GameRules.buildCost(j.getBuildingType(), j.getToLevel()-1);
      c.setWood(c.getWood()+r[0]); c.setStone(c.getStone()+r[1]); c.setSilver(c.getSilver()+r[2]);
    } else {
      UnitType u=j.getUnitType();
      c.setWood(c.getWood()+(long)u.costWood*j.getBatch());
      c.setStone(c.getStone()+(long)u.costStone*j.getBatch());
      c.setSilver(c.getSilver()+(long)u.costSilver*j.getBatch());
    }
    cities.save(c);
    QueueType qt=j.getQueueType(); jobs.delete(j);
    List<BuildJob> q = jobs.findByCityIdAndQueueTypeOrderByPositionAsc(cityId, qt);
    Instant now=Instant.now();
    for (int i=0;i<q.size();i++){ BuildJob b=q.get(i); b.setPosition(i);
      if (i==0 && b.getFinishAt()==null){ b.setStartedAt(now); b.setFinishAt(now.plusSeconds(b.getTotalSeconds())); } }
    jobs.saveAll(q);
  }

  /** Gold cost to instantly finish a job: 1 gold per minute of remaining time, min 1. */
  public static int rushCost(long remainingSeconds){ return (int)Math.max(1, Math.ceil(remainingSeconds/60.0)); }

  @Transactional
  public void finishWithGold(Long playerId, Long cityId, Long jobId){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    BuildJob j = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
    if (!Objects.equals(j.getCityId(), cityId)) throw new IllegalStateException("Wrong city");
    if (j.getPosition() != 0) throw new IllegalStateException("Only the active job can be rushed");
    Instant now = Instant.now();
    long rem = j.getFinishAt()==null ? j.getTotalSeconds() : Math.max(0, Duration.between(now, j.getFinishAt()).getSeconds());
    int cost = rushCost(rem);
    Player p = players.findById(playerId).orElseThrow();
    if (p.getGold() < cost) throw new IllegalStateException("Not enough gold (need " + cost + ")");
    p.setGold(p.getGold() - cost); players.save(p);
    j.setFinishAt(now); jobs.save(j);
    cityService.finalizeJobs(c, now);
  }

  @Transactional
  public void rename(Long playerId, Long cityId, String name){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    cities.renameById(cityId, name.length()>40 ? name.substring(0,40) : name);
  }

  @Transactional
  public Movement colonize(Long playerId, Long cityId, Long islandId, int slot){
    City src = owned(playerId, cityId);
    Player p = players.findById(playerId).orElseThrow();
    long owned = cities.countByPlayerId(playerId);
    if (owned >= GameRules.citySlots(p.getLevel()))
      throw new IllegalStateException("No free colony slots — level up to settle more cities");
    if (cities.findByIslandIdAndSlot(islandId, slot).isPresent())
      throw new IllegalStateException("That plot is already occupied");
    if (!afford(src, COLONY_COST[0], COLONY_COST[1], COLONY_COST[2]))
      throw new IllegalStateException("Not enough resources to outfit a colony ship");
    pay(src, COLONY_COST[0], COLONY_COST[1], COLONY_COST[2]); cities.save(src);

    int secs = travel.seconds(src.getIslandId(), islandId, 11);
    Movement m = new Movement();
    m.setWorldId(src.getWorldId()); m.setPlayerId(playerId); m.setSourceCityId(cityId);
    m.setTargetIslandId(islandId); m.setTargetSlot(slot); m.setPhase(MovementPhase.COLONY);
    m.setArriveAt(Instant.now().plusSeconds(secs));
    return movements.save(m);
  }

  @Transactional
  public Movement raid(Long playerId, Long cityId, Long targetCityId, Map<String,Integer> army){
    City src = owned(playerId, cityId);
    City tgt = cities.findById(targetCityId).orElseThrow(() -> new IllegalArgumentException("Target not found"));
    if (Objects.equals(tgt.getPlayerId(), playerId)) throw new IllegalStateException("Cannot raid your own city");
    int minSpeed = 99;
    for (var e : army.entrySet()){
      UnitType u = UnitType.valueOf(e.getKey().toUpperCase());
      CityUnit cu = units.findByCityId(cityId).stream().filter(x->x.getType()==u).findFirst()
        .orElseThrow(() -> new IllegalStateException("No such troops"));
      if (cu.getCount() < e.getValue()) throw new IllegalStateException("Not enough " + u);
      cu.setCount(cu.getCount()-e.getValue()); units.save(cu);
      minSpeed = Math.min(minSpeed, u.speed);
    }
    if (army.isEmpty()) throw new IllegalArgumentException("Select at least one unit");
    int secs = travel.seconds(src.getIslandId(), tgt.getIslandId(), minSpeed==99?15:minSpeed);
    Movement m = new Movement();
    m.setWorldId(src.getWorldId()); m.setPlayerId(playerId); m.setSourceCityId(cityId);
    m.setTargetCityId(targetCityId); m.setPhase(MovementPhase.OUT);
    m.setUnits(new HashMap<>(army)); m.setArriveAt(Instant.now().plusSeconds(secs));
    return movements.save(m);
  }

  private boolean hasResearch(Long cityId, ResearchType t){
    return research.findByCityId(cityId).stream().anyMatch(r->r.getType()==t);
  }
}
