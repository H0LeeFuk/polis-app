package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Assembles the full client-facing game state for a player + a chosen active city. */
@Service
public class GameService {
  private final CityService cityService; private final RankingService ranking;
  private final PlayerRepo players; private final CityRepo cities; private final IslandRepo islands;
  private final BuildingRepo buildings; private final UnitRepo units; private final ResearchRepo research;
  private final JobRepo jobs; private final AllianceRepo alliances; private final MovementRepo movements;
  private final UnitCatalog catalog;

  public GameService(CityService cityService, RankingService ranking, PlayerRepo players, CityRepo cities,
                     IslandRepo islands, BuildingRepo buildings, UnitRepo units, ResearchRepo research,
                     JobRepo jobs, AllianceRepo alliances, MovementRepo movements, UnitCatalog catalog){
    this.cityService=cityService; this.ranking=ranking; this.players=players; this.cities=cities; this.islands=islands;
    this.buildings=buildings; this.units=units; this.research=research; this.jobs=jobs; this.alliances=alliances; this.movements=movements;
    this.catalog=catalog;
  }

  @Transactional
  public Map<String,Object> state(Long playerId, Long cityId){
    Player p = players.findById(playerId).orElseThrow();
    List<City> owned = cities.findByPlayerId(playerId);
    owned.forEach(cityService::sync);

    City active = (cityId!=null
        ? owned.stream().filter(c->c.getId().equals(cityId)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Not your city"))
        : owned.stream().filter(City::isCapital).findFirst().orElse(owned.get(0)));

    Map<String,String> islName = new HashMap<>();
    islands.findByWorldId(p.getWorldId()).forEach(i->islName.put(String.valueOf(i.getId()), i.getName()));

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("player", playerDto(p, owned.size()));
    out.put("cities", owned.stream().map(c->citySummary(c, islName)).toList());
    out.put("active", cityDetail(active, p, islName));
    return out;
  }

  private Map<String,Object> playerDto(Player p, int cityCount){
    Map<String,Object> m=new LinkedHashMap<>();
    m.put("id",p.getId()); m.put("username",p.getUsername()); m.put("level",p.getLevel());
    m.put("combatPoints",p.getCombatPoints()); m.put("combatToNext",GameRules.levelReq(p.getLevel()));
    m.put("gold",p.getGold());
    m.put("citySlots",GameRules.citySlots(p.getLevel())); m.put("ownedCities",cityCount);
    m.put("totalPoints",ranking.playerPoints(p.getId()));
    if (p.getAllianceId()!=null) alliances.findById(p.getAllianceId()).ifPresent(a->m.put("alliance",a.getName()));
    return m;
  }
  private Map<String,Object> citySummary(City c, Map<String,String> islName){
    Map<String,Object> m=new LinkedHashMap<>();
    m.put("id",c.getId()); m.put("name",c.getName()); m.put("points",c.getPoints());
    m.put("capital",c.isCapital()); m.put("island",islName.get(String.valueOf(c.getIslandId())));
    return m;
  }

  private Map<String,Object> cityDetail(City c, Player p, Map<String,String> islName){
    Long id=c.getId();
    Map<BuildingType,Integer> lv = cityService.levels(id);
    boolean bounty = research.findByCityId(id).stream().anyMatch(r->r.getType()==ResearchType.BOUNTY);
    Set<ResearchType> done = new HashSet<>();
    research.findByCityId(id).forEach(r->done.add(r.getType()));
    int senate = lv.get(BuildingType.SENATE);

    List<Map<String,Object>> bld = new ArrayList<>();
    for (BuildingType t : BuildingType.values()){
      int l = lv.get(t); long[] cost = GameRules.buildCost(t, l);
      Map<String,Object> b=new LinkedHashMap<>();
      b.put("type",t.name()); b.put("level",l); b.put("max",t.max); b.put("pop",t.pop);
      b.put("cost",List.of(cost[0],cost[1],cost[2]));
      b.put("seconds",GameRules.buildSeconds(t,l,senate));
      b.put("atMax", l>=t.max);
      if (l < t.max) b.put("benefit", buildingBenefit(t, l));   // what the next level gives
      bld.add(b);
    }

    Race cityRace = c.getRace()==null ? Race.HUMANS : c.getRace();
    List<Map<String,Object>> trainable = new ArrayList<>();
    for (UnitType u : catalog.all()){
      // each race trains its own roster (+ any shared/neutral units with no race tag)
      if (u.getRace()!=null && u.getRace()!=cityRace) continue;
      QueueType from = u.getFromQueue();
      Map<String,Object> t=new LinkedHashMap<>();
      t.put("type",u.getName()); t.put("from",from.name()); t.put("kind",u.getKind().name());
      // attack element: explicit for elites, else the city race's element; siege has none
      t.put("siege", u.isSiege());
      t.put("attackElement", u.isSiege() ? null : (u.getAttackElement()!=null ? u.getAttackElement().name() : cityRace.element.name()));
      t.put("atk",u.getAttack());
      t.put("defFire",u.getDefenseFire()); t.put("defWind",u.getDefenseWind());
      t.put("defEarth",u.getDefenseEarth()); t.put("defWater",u.getDefenseWater());
      t.put("speed",u.getSpeedMinutesPerTile()); t.put("pop",u.getPopulationCost()); t.put("carry",u.getCarryCapacity());
      t.put("movementClass",u.getMovementClass().name());
      t.put("transportCapacity",u.getTransportCapacity());
      t.put("requiresTransport",u.isRequiresTransport());
      t.put("cost",List.of(u.getCostWood(),u.getCostStone(),u.getCostWheat()));
      t.put("elite", u.isElite());
      t.put("costSpecial", u.getCostSpecial());
      t.put("specialResource", u.isElite() ? cityRace.specialResource.name() : null);
      t.put("seconds",GameRules.unitSeconds(u, lv.get(from==QueueType.HARBOR?BuildingType.HARBOR:BuildingType.BARRACKS)));
      boolean unlocked = u.getResearchRequired()==null || done.contains(ResearchType.valueOf(u.getResearchRequired()));
      t.put("unlocked", unlocked);
      trainable.add(t);
    }

    List<Map<String,Object>> rsr = new ArrayList<>();
    for (ResearchType r : ResearchType.values()){
      Map<String,Object> m=new LinkedHashMap<>();
      m.put("type",r.name()); m.put("req",r.req); m.put("done",done.contains(r));
      m.put("cost",List.of(r.costWood,r.costStone,r.costWheat));
      rsr.add(m);
    }

    List<Map<String,Object>> us = units.findByCityId(id).stream().filter(u->u.getCount()>0)
      .map(u->{ Map<String,Object> m=new LinkedHashMap<>(); m.put("type",u.getType()); m.put("count",u.getCount()); return m; }).toList();

    Map<String,List<Map<String,Object>>> queues = new LinkedHashMap<>();
    for (QueueType qt : QueueType.values()){
      List<Map<String,Object>> list = new ArrayList<>();
      for (BuildJob j : jobs.findByCityIdAndQueueTypeOrderByPositionAsc(id, qt)){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("id",j.getId()); m.put("position",j.getPosition()); m.put("totalSeconds",j.getTotalSeconds());
        m.put("finishAt", j.getFinishAt()==null?null:j.getFinishAt().toString());
        if (qt==QueueType.BUILDING){ m.put("label", j.getBuildingType().name()); m.put("toLevel", j.getToLevel()); }
        else { m.put("label", j.getUnitType()); m.put("batch", j.getBatch()); }
        list.add(m);
      }
      queues.put(qt.name(), list);
    }

    List<Map<String,Object>> moves = movements.findByPlayerIdAndResolvedFalse(p.getId()).stream().map(m->{
      Map<String,Object> x=new LinkedHashMap<>();
      x.put("id",m.getId()); x.put("phase",m.getPhase().name()); x.put("arriveAt",m.getArriveAt().toString());
      x.put("target", m.getTargetCityId()!=null ? cities.findById(m.getTargetCityId()).map(City::getName).orElse("?")
                       : islName.get(String.valueOf(m.getTargetIslandId())));
      return x;
    }).toList();

    Map<String,Object> m=new LinkedHashMap<>();
    m.put("id",id); m.put("name",c.getName()); m.put("capital",c.isCapital());
    m.put("island",islName.get(String.valueOf(c.getIslandId()))); m.put("points",c.getPoints());
    m.put("race", c.getRace()==null ? null : c.getRace().dto());
    long cap=GameRules.storeCap(lv.get(BuildingType.WAREHOUSE));
    Race rRace = c.getRace()==null ? Race.HUMANS : c.getRace();
    ResourceType special = rRace.specialResource;
    Map<String,Object> res=new LinkedHashMap<>();
    res.put("wood",(long)c.getWood()); res.put("stone",(long)c.getStone()); res.put("wheat",(long)c.getWheat());
    res.put("capacity",cap);
    res.put("special",(long)c.get(special)); res.put("specialResource",special.name());
    res.put("woodProd",(long)GameRules.prodPerHour(lv.get(BuildingType.TIMBER)));
    res.put("stoneProd",(long)GameRules.prodPerHour(lv.get(BuildingType.QUARRY)));
    res.put("wheatProd",(long)GameRules.prodPerHour(lv.get(BuildingType.MINE)));
    res.put("specialProd",(long)GameRules.prodPerHour(lv.get(BuildingType.EXTRACTOR)));
    // any looted/traded special resources of other races the city is currently holding
    Map<String,Object> extra=new LinkedHashMap<>();
    for (ResourceType rt : ResourceType.values())
      if (rt.isSpecial() && rt!=special && c.get(rt)>0) extra.put(rt.name(),(long)c.get(rt));
    res.put("otherSpecials", extra);
    m.put("resources",res);
    m.put("pop", cityService.popUsed(id)); m.put("maxPop", cityService.maxPop(id, bounty));
    m.put("buildings",bld); m.put("queues",queues); m.put("units",us);
    m.put("trainable",trainable); m.put("research",rsr); m.put("movements",moves);
    return m;
  }

  /** Human-readable benefit of upgrading {@code t} from level {@code l} to {@code l+1}. */
  private String buildingBenefit(BuildingType t, int l){
    int n = l + 1;
    return switch (t){
      case TIMBER    -> "Wood "   + (long)GameRules.prodPerHour(l) + "/h → " + (long)GameRules.prodPerHour(n) + "/h";
      case QUARRY    -> "Stone "  + (long)GameRules.prodPerHour(l) + "/h → " + (long)GameRules.prodPerHour(n) + "/h";
      case MINE      -> "Wheat " + (long)GameRules.prodPerHour(l) + "/h → " + (long)GameRules.prodPerHour(n) + "/h";
      case EXTRACTOR -> "Special resource " + (long)GameRules.prodPerHour(l) + "/h → " + (long)GameRules.prodPerHour(n) + "/h";
      case FARM      -> "Population cap " + GameRules.farmPop(l) + " → " + GameRules.farmPop(n);
      case SENATE    -> "Build speed +" + senatePct(l) + "% → +" + senatePct(n) + "% faster construction";
      case BARRACKS  -> "Training +" + trainPct(l) + "% → +" + trainPct(n) + "% faster";
      case HARBOR    -> "Ship training +" + trainPct(l) + "% → +" + trainPct(n) + "% faster";
      case WAREHOUSE -> "Raises resource storage capacity";
      case LIBRARY   -> "Research speed +" + libPct(l) + "% → +" + libPct(n) + "% (unlocks research up to level " + n + ")";
      case WALL      -> "Strengthens the city's defences";
      case MARKET    -> "Bigger trade convoys, more at once, faster delivery";
    };
  }
  private static int senatePct(int level){ return (int)Math.round(Math.min(0.6, level * 0.04) * 100); }
  private static int trainPct(int level){ return (int)Math.round(Math.min(0.5, Math.max(0, level - 1) * 0.03) * 100); }
  private static int libPct(int level){ return (int)Math.round(LibraryService.librarySpeedBonus(level) * 100); }
}
