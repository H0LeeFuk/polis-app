package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Background loop that advances the world: completes due build/training jobs and
 * resolves arriving movements (colony foundings, raids, returns). Players also get
 * their own city synced on every read, so this mainly drives offline/background cities.
 */
@Component
public class TickScheduler {
  private final CityService cityService;
  private final CityFactory cityFactory;
  private final CityRepo cities; private final UnitRepo units; private final JobRepo jobs;
  private final MovementRepo movements; private final PlayerRepo players;

  public TickScheduler(CityService cityService, CityFactory cityFactory, CityRepo cities, UnitRepo units,
                       JobRepo jobs, MovementRepo movements, PlayerRepo players){
    this.cityService=cityService; this.cityFactory=cityFactory; this.cities=cities; this.units=units;
    this.jobs=jobs; this.movements=movements; this.players=players;
  }

  @Scheduled(fixedDelayString = "${polis.tick.interval-ms}")
  @Transactional
  public void tick(){
    Instant now = Instant.now();

    // finalize due construction/training across all cities
    Set<Long> touched = new HashSet<>();
    for (BuildJob j : jobs.findDueActiveJobs(now)) touched.add(j.getCityId());
    for (Long cid : touched) cities.findById(cid).ifPresent(c -> cityService.finalizeJobs(c, now));

    // resolve movements
    for (Movement m : movements.findDue(now)){
      switch (m.getPhase()){
        case COLONY -> resolveColony(m);
        case OUT    -> resolveRaid(m, now);
        case RETURN -> resolveReturn(m);
      }
      m.setResolved(true); movements.save(m);
    }
  }

  private void resolveColony(Movement m){
    if (cities.findByIslandIdAndSlot(m.getTargetIslandId(), m.getTargetSlot()).isPresent()) return; // plot taken
    Player p = players.findById(m.getPlayerId()).orElse(null); if (p==null) return;
    String base = cities.findByPlayerIdAndCapitalTrue(p.getId()).map(City::getName).orElse(p.getUsername());
    cityFactory.createPlayerCity(m.getWorldId(), p.getId(), m.getTargetIslandId(), m.getTargetSlot(),
        base.split(" ")[0] + " Colony", false);
  }

  private void resolveRaid(Movement m, Instant now){
    City tgt = cities.findById(m.getTargetCityId()).orElse(null);
    if (tgt == null) { returnArmy(m, m.getUnits(), null); return; }
    double atk = armyAtk(m.getUnits());
    double def = tgt.getPower();
    Map<String,Integer> survivors = new HashMap<>();
    Map<String,Long> loot = null;
    boolean win = atk >= def;
    double lossFrac = win ? Math.min(0.85, (def/Math.max(1,atk))*0.7)
                          : Math.min(0.95, 0.5 + (1-atk/Math.max(1,def))*0.5);
    for (var e : m.getUnits().entrySet()){
      int keep = (int)Math.round(e.getValue()*(1-lossFrac));
      if (keep>0) survivors.put(e.getKey(), keep);
    }
    if (win){
      tgt.setPower(Math.max(40, tgt.getPower()*0.45+20));
      long carry = (long)(armyCarry(survivors));
      long pool = (long)(tgt.getWood()+tgt.getStone()+tgt.getSilver());
      long want = Math.min(carry, pool); loot = new HashMap<>();
      if (pool>0){
        long lw=(long)(want*tgt.getWood()/pool), ls=(long)(want*tgt.getStone()/pool), lv=(long)(want*tgt.getSilver()/pool);
        loot.put("WOOD",lw); loot.put("STONE",ls); loot.put("SILVER",lv);
        tgt.setWood(tgt.getWood()-lw); tgt.setStone(tgt.getStone()-ls); tgt.setSilver(tgt.getSilver()-lv);
      }
    } else {
      tgt.setPower(Math.max(40, tgt.getPower()*(1-atk/Math.max(1,def)*0.4)));
    }
    cities.save(tgt);

    // award combat points to the raider (1 per population killed + an estimate of enemy pop killed)
    int lostPop = 0;
    for (var e : m.getUnits().entrySet()){
      UnitType u = UnitType.valueOf(e.getKey().toUpperCase());
      lostPop += u.pop * (e.getValue() - survivors.getOrDefault(e.getKey(),0));
    }
    int enemyKilled = (int)Math.round(win ? def/18.0 : atk/22.0);
    awardCombat(m.getPlayerId(), lostPop + enemyKilled);

    if (survivors.isEmpty()) return; // none returned
    // schedule the return leg
    int speed = 99; for (String k : survivors.keySet()) speed = Math.min(speed, UnitType.valueOf(k.toUpperCase()).speed);
    Movement ret = new Movement();
    ret.setWorldId(m.getWorldId()); ret.setPlayerId(m.getPlayerId()); ret.setSourceCityId(m.getSourceCityId());
    ret.setTargetCityId(m.getTargetCityId()); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(survivors); ret.setLoot(loot);
    long secs = Math.max(5, now.getEpochSecond() - m.getDepartAt().getEpochSecond());
    ret.setArriveAt(now.plusSeconds(secs));
    movements.save(ret);
  }

  private void resolveReturn(Movement m){ returnArmy(m, m.getUnits(), m.getLoot()); }

  private void returnArmy(Movement m, Map<String,Integer> army, Map<String,Long> loot){
    City home = cities.findById(m.getSourceCityId()).orElse(null); if (home==null) return;
    for (var e : army.entrySet()){
      UnitType u = UnitType.valueOf(e.getKey().toUpperCase());
      CityUnit cu = units.findByCityId(home.getId()).stream().filter(x->x.getType()==u).findFirst()
        .orElseGet(()->new CityUnit(home.getId(), u, 0));
      cu.setCount(cu.getCount()+e.getValue()); units.save(cu);
    }
    if (loot != null){
      long cap = cityService.capacity(home.getId());
      home.setWood(Math.min(cap, home.getWood()+loot.getOrDefault("WOOD",0L)));
      home.setStone(Math.min(cap, home.getStone()+loot.getOrDefault("STONE",0L)));
      home.setSilver(Math.min(cap, home.getSilver()+loot.getOrDefault("SILVER",0L)));
      cities.save(home);
    }
  }

  private void awardCombat(Long playerId, int pts){
    if (playerId==null || pts<=0) return;
    Player p = players.findById(playerId).orElse(null); if (p==null) return;
    int cp = p.getCombatPoints()+pts, lvl = p.getLevel();
    while (cp >= GameRules.levelReq(lvl)){ cp -= GameRules.levelReq(lvl); lvl++; }
    p.setCombatPoints(cp); p.setLevel(lvl); players.save(p);
  }

  static double armyAtk(Map<String,Integer> a){ double s=0; for (var e:a.entrySet()) s+=UnitType.valueOf(e.getKey().toUpperCase()).atk*e.getValue(); return s; }
  static double armyCarry(Map<String,Integer> a){ double s=0; for (var e:a.entrySet()) s+=UnitType.valueOf(e.getKey().toUpperCase()).carry*e.getValue(); return s; }
}
