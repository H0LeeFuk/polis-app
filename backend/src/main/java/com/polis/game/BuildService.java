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

  private final CityService cityService;
  private final CityRepo cities;
  private final BuildingRepo buildings;
  private final UnitRepo units;
  private final ResearchRepo research;
  private final JobRepo jobs;
  private final MovementRepo movements;
  private final PlayerRepo players;
  private final TravelTimeService travel;
  private final UnitCatalog catalog;
  private final HeroService heroes;
  private final MissionService missions;
  private final LibraryService library;

  public BuildService(CityService cityService, CityRepo cities, BuildingRepo buildings, UnitRepo units,
                      ResearchRepo research, JobRepo jobs, MovementRepo movements, PlayerRepo players,
                      TravelTimeService travel, UnitCatalog catalog, HeroService heroes, MissionService missions,
                      LibraryService library){
    this.cityService=cityService; this.cities=cities; this.buildings=buildings; this.units=units;
    this.research=research; this.jobs=jobs; this.movements=movements; this.players=players;
    this.travel=travel; this.catalog=catalog; this.heroes=heroes; this.missions=missions; this.library=library;
  }

  private City owned(Long playerId, Long cityId){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    return cityService.sync(c);
  }
  private boolean afford(City c, long w, long s, long wh){ return c.getWood()>=w && c.getStone()>=s && c.getWheat()>=wh; }
  private void pay(City c, long w, long s, long wh){ c.setWood(c.getWood()-w); c.setStone(c.getStone()-s); c.setWheat(c.getWheat()-wh); }

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
  public void train(Long playerId, Long cityId, String unitName, int count){
    City c = owned(playerId, cityId);
    if (count <= 0) throw new IllegalArgumentException("Count must be positive");
    UnitType type = catalog.get(unitName);
    // own-race production only: a city may build only its own race's roster (mix armies at dispatch)
    if (type.getRace() != null && c.getRace() != null && type.getRace() != c.getRace())
      throw new IllegalStateException("This city's " + c.getRace().displayName + " barracks cannot train " + type.getName());
    if (type.getResearchRequired() != null && !hasResearch(cityId, ResearchType.valueOf(type.getResearchRequired())))
      throw new IllegalStateException("Requires research: " + type.getResearchRequired());
    // Library siege gate: siege engines need the Siegecraft research
    if (type.isSiege() && !library.effects(cityId).has("siege"))
      throw new IllegalStateException("Requires the Library research: Siegecraft");
    // elite units cost a special resource — only the city race's own special is available
    ResourceType special = (type.getCostSpecial() > 0 && c.getRace() != null) ? c.getRace().specialResource : null;
    QueueType from = type.getFromQueue();
    int fromLevel = cityService.level(cityId, from==QueueType.HARBOR ? BuildingType.HARBOR : BuildingType.BARRACKS);
    if (fromLevel <= 0) throw new IllegalStateException("Build the " + from + " first");
    if (jobs.countByCityIdAndQueueType(cityId, from) >= UNIT_QUEUE_MAX)
      throw new IllegalStateException("Training queue is full");
    int freePop = cityService.maxPop(cityId, hasResearch(cityId, ResearchType.BOUNTY)) - cityService.popUsed(cityId);
    count = Math.min(count, freePop / Math.max(1,type.getPopulationCost()));
    int byRes = (int) Math.min(Math.min(
        type.getCostWood() >0 ? (long)(c.getWood()/type.getCostWood())   : Long.MAX_VALUE,
        type.getCostStone()>0 ? (long)(c.getStone()/type.getCostStone()) : Long.MAX_VALUE),
        type.getCostWheat()>0 ? (long)(c.getWheat()/type.getCostWheat()) : Long.MAX_VALUE);
    if (special != null && type.getCostSpecial() > 0)
      byRes = (int) Math.min(byRes, (long)(c.get(special)/type.getCostSpecial()));
    count = Math.min(count, byRes);
    if (count <= 0) throw new IllegalStateException("Not enough population or resources");
    pay(c, (long)type.getCostWood()*count, (long)type.getCostStone()*count, (long)type.getCostWheat()*count);
    if (special != null) c.add(special, -(long)type.getCostSpecial()*count);
    cities.save(c);
    int total = (int)(GameRules.unitSeconds(type, fromLevel) * count * library.effects(cityId).trainTimeMult());
    BuildJob job = new BuildJob(); job.setUnitType(type.getName()); job.setBatch(count);
    enqueue(c, job, from, total);
  }

  @Transactional
  public void research(Long playerId, Long cityId, ResearchType type){
    City c = owned(playerId, cityId);
    if (cityService.level(cityId, BuildingType.LIBRARY) < type.req)
      throw new IllegalStateException("Library level too low (needs " + type.req + ")");
    if (hasResearch(cityId, type)) throw new IllegalStateException("Already researched");
    double rm = c.getRace()!=null ? c.getRace().researchCostMult : 1.0;   // HUMANS research discount
    long cw=Math.round(type.costWood*rm), cs=Math.round(type.costStone*rm), cwh=Math.round(type.costWheat*rm);
    if (!afford(c, cw, cs, cwh)) throw new IllegalStateException("Not enough resources");
    pay(c, cw, cs, cwh); cities.save(c);
    research.save(new CityResearch(cityId, type));
    missions.record(playerId, MissionObjectiveType.RESEARCH_COMPLETE, 1);
  }

  @Transactional
  public void cancel(Long playerId, Long cityId, Long jobId){
    owned(playerId, cityId);
    BuildJob j = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
    if (!Objects.equals(j.getCityId(), cityId)) throw new IllegalStateException("Wrong city");
    City c = cities.findById(cityId).orElseThrow();
    if (j.getQueueType()==QueueType.BUILDING){
      long[] r = GameRules.buildCost(j.getBuildingType(), j.getToLevel()-1);
      c.setWood(c.getWood()+r[0]); c.setStone(c.getStone()+r[1]); c.setWheat(c.getWheat()+r[2]);
    } else {
      UnitType u=catalog.get(j.getUnitType());
      c.setWood(c.getWood()+(long)u.getCostWood()*j.getBatch());
      c.setStone(c.getStone()+(long)u.getCostStone()*j.getBatch());
      c.setWheat(c.getWheat()+(long)u.getCostWheat()*j.getBatch());
      if (u.getCostSpecial()>0 && c.getRace()!=null) c.add(c.getRace().specialResource, (long)u.getCostSpecial()*j.getBatch());
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
    // A job can be rushed only if no EARLIER job in the same queue targets the same building/unit —
    // e.g. you may gold a Barracks upgrade while the Farm is upgrading, but not Farm→21 before Farm→20.
    String key = jobKey(j);
    for (BuildJob other : jobs.findByCityIdAndQueueTypeOrderByPositionAsc(cityId, j.getQueueType()))
      if (other.getPosition() < j.getPosition() && key.equals(jobKey(other)))
        throw new IllegalStateException("Finish the earlier " + key + " upgrade first");
    Instant now = Instant.now();
    long rem = j.getFinishAt()==null ? j.getTotalSeconds() : Math.max(0, Duration.between(now, j.getFinishAt()).getSeconds());
    int cost = rushCost(rem);
    Player p = players.findById(playerId).orElseThrow();
    if (p.getGold() < cost) throw new IllegalStateException("Not enough gold (need " + cost + ")");
    p.setGold(p.getGold() - cost); players.save(p);
    cityService.forceComplete(c, j);
  }

  /** Identity of what a job produces — used to block rushing past a same-target predecessor. */
  private String jobKey(BuildJob j){
    return j.getQueueType()==QueueType.BUILDING ? j.getBuildingType().name() : j.getUnitType();
  }

  @Transactional
  public void rename(Long playerId, Long cityId, String name){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    cities.renameById(cityId, name.length()>40 ? name.substring(0,40) : name);
  }

  @Transactional
  public Movement attack(Long playerId, Long cityId, Long targetCityId, Map<String,Integer> army, Long heroId){
    City src = owned(playerId, cityId);
    City tgt = cities.findById(targetCityId).orElseThrow(() -> new IllegalArgumentException("Target not found"));
    if (Objects.equals(tgt.getPlayerId(), playerId)) throw new IllegalStateException("Cannot attack your own city");
    // an army OR a lone hero may march — but never an empty order
    if (army.isEmpty() && heroId == null) throw new IllegalArgumentException("Select at least one unit or a hero");
    // LAND troops — and a land (non-flying/swimming) hero — can't cross open water without transport ships
    Hero hero = heroId != null ? heroes.requireOwned(playerId, heroId) : null;
    int heroLoad = hero != null ? travel.heroLandLoad(hero.getRace()) : 0;
    travel.requireTransport(army, src.getIslandId(), tgt.getIslandId(), heroLoad);
    deductGarrison(cityId, army);
    long secs = travel.travelTime(cityId, targetCityId, army).getSeconds();
    if (src.getRace()!=null) secs = (long)(secs * src.getRace().travelMult);          // race march pace
    secs = (long)(secs * library.effects(cityId).travelMult());                       // Library: Wayfinding etc.
    if (hero != null) secs = (long)(secs * heroes.travelMult(hero));                  // Cunning / Forced March
    Movement m = new Movement();
    m.setWorldId(src.getWorldId()); m.setPlayerId(playerId); m.setSourceCityId(cityId);
    m.setTargetCityId(targetCityId); m.setPhase(MovementPhase.OUT);
    m.setUnits(new HashMap<>(army)); m.setArriveAt(Instant.now().plusSeconds(Math.max(5, secs)));
    Movement saved = movements.save(m);
    if (heroId != null) heroes.sendHero(playerId, heroId, cityId, saved.getId());
    // mission: launching an attack on a real distinct player
    if (tgt.getPlayerId() != null && !Objects.equals(tgt.getPlayerId(), playerId))
      missions.record(playerId, MissionObjectiveType.ATTACK_PLAYER, 1);
    return saved;
  }

  /** Removes troops from a city garrison, validating each stack has enough. Shared by raids and node moves. */
  @Transactional
  public void deductGarrison(Long cityId, Map<String,Integer> army){
    for (var e : army.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      String name = catalog.get(e.getKey()).getName();
      CityUnit cu = units.findByCityId(cityId).stream().filter(x->x.getType().equalsIgnoreCase(name)).findFirst()
        .orElseThrow(() -> new IllegalStateException("No such troops: " + name));
      if (cu.getCount() < e.getValue()) throw new IllegalStateException("Not enough " + name);
      cu.setCount(cu.getCount()-e.getValue()); units.save(cu);
    }
  }

  private boolean hasResearch(Long cityId, ResearchType t){
    return research.findByCityId(cityId).stream().anyMatch(r->r.getType()==t);
  }
}
