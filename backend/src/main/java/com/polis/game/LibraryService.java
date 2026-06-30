package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * The Library: a per-city research tree. Library level (the LIBRARY building) grants research
 * points (4/level → 80 at level 20). The full tree costs ~130, so
 * cities must specialize. Research takes time (one at a time per city); re-spec refunds all.
 *
 * Effects stack on top of race bonuses. Documented order everywhere: race → Library → hero → items.
 */
@Service
public class LibraryService {
  private final CityRepo cities;
  private final BuildingRepo buildings;
  private final CityLibraryResearchRepo research;
  private final UnitRepo unitRepo;

  public LibraryService(CityRepo cities, BuildingRepo buildings, CityLibraryResearchRepo research, UnitRepo unitRepo){
    this.cities = cities; this.buildings = buildings; this.research = research; this.unitRepo = unitRepo;
  }

  /** Bastion "City Guard": summon a batch of weak MILITIA into the garrison, on a 5h cooldown. */
  @Transactional
  public Map<String,Object> callGuard(Long playerId, Long cityId){
    City c = ownedSynced(playerId, cityId);
    if (!effects(cityId).has("cityGuard")) throw new IllegalStateException("Requires the Library research: City Guard");
    Instant now = Instant.now();
    if (c.getCityGuardReadyAt()!=null && c.getCityGuardReadyAt().isAfter(now))
      throw new IllegalStateException("The City Guard is still resting");
    int batch = 40;
    CityUnit cu = unitRepo.findByCityId(cityId).stream()
        .filter(x->x.getType().equalsIgnoreCase("MILITIA")).findFirst()
        .orElseGet(()->new CityUnit(cityId, "MILITIA", 0));
    cu.setCount(cu.getCount()+batch); unitRepo.save(cu);
    c.setCityGuardReadyAt(now.plusSeconds(5*3600)); cities.save(c);
    return Map.of("ok", true, "summoned", batch, "readyAt", c.getCityGuardReadyAt().toString());
  }

  /** Accumulated, multiplier-ready effects of a city's completed Library research. */
  public record LibEffects(double attackMult, double defenseMult,
                           double defFireMult, double defWindMult, double defEarthMult, double defWaterMult,
                           double travelMult, double prodMult, double lootMult, double trainTimeMult,
                           double navalTrainTimeMult,
                           double storageMult, double specialProdMult, double buildTimeMult,
                           double lootStolenMult, double lootProtectFrac, double siegeWallMult,
                           Set<String> flags){
    public static LibEffects none(){ return new LibEffects(1,1,1,1,1,1,1,1,1,1,1, 1,1,1,1,0,1, Set.of()); }
    public boolean has(String f){ return flags.contains(f); }
    public double defElementMult(com.polis.domain.Element e){
      return switch (e){ case FIRE -> defFireMult; case WIND -> defWindMult; case EARTH -> defEarthMult; case WATER -> defWaterMult; };
    }
  }

  public int libraryLevel(Long cityId){
    return buildings.findByCityId(cityId).stream()
        .filter(b -> b.getType()==BuildingType.LIBRARY).map(CityBuilding::getLevel).findFirst().orElse(0);
  }

  // --- lazy settle of in-progress research -----------------------------------

  @Transactional
  public void settle(Long cityId, Instant now){
    for (CityLibraryResearch r : research.findByCityId(cityId))
      if (r.getStatus()==CityLibraryResearch.Status.RESEARCHING && r.getCompletesAt()!=null && !r.getCompletesAt().isAfter(now)){
        r.setStatus(CityLibraryResearch.Status.COMPLETED); research.save(r);
      }
  }

  /** Tick sweep: complete any due research across all cities. */
  @Transactional
  public void completeDue(Instant now){
    for (CityLibraryResearch r : research.findByStatusAndCompletesAtLessThanEqual(CityLibraryResearch.Status.RESEARCHING, now)){
      r.setStatus(CityLibraryResearch.Status.COMPLETED); research.save(r);
    }
  }

  // --- effects ---------------------------------------------------------------

  @Transactional
  public LibEffects effects(Long cityId){
    settle(cityId, Instant.now());
    double attack=0, defense=0, dFire=0, dWind=0, dEarth=0, dWater=0, travel=0, prod=0, loot=0, train=0, navalTrain=0;
    double storage=0, specialProd=0, build=0, lootStolen=0, lootProtect=0, siegeWall=0;
    Set<String> flags = new HashSet<>();
    for (CityLibraryResearch cr : research.findByCityId(cityId)){
      if (cr.getStatus()!=CityLibraryResearch.Status.COMPLETED) continue;
      LibraryTree.Research def = LibraryTree.byId(cr.getResearchId());
      if (def==null) continue;
      for (var e : def.effects().entrySet()){
        switch (e.getKey()){
          case "attack" -> attack += e.getValue();
          case "defense" -> defense += e.getValue();
          case "defFire"  -> dFire  += e.getValue();
          case "defWind"  -> dWind  += e.getValue();
          case "defEarth" -> dEarth += e.getValue();
          case "defWater" -> dWater += e.getValue();
          case "travel", "navalTravel", "cityTravel" -> travel += e.getValue();
          case "production" -> prod += e.getValue();
          case "specialProd" -> specialProd += e.getValue();
          case "loot" -> loot += e.getValue();
          case "lootStolen" -> lootStolen += e.getValue();
          case "lootProtect" -> lootProtect += e.getValue();
          case "storage" -> storage += e.getValue();
          case "buildTime" -> build += e.getValue();
          case "siegeWall" -> siegeWall += e.getValue();
          case "trainTime" -> train += e.getValue();
          case "navalTrainSpeed" -> navalTrain += e.getValue();
          default -> {}
        }
      }
      flags.addAll(def.flags());
    }
    return new LibEffects(1+attack, 1+defense, 1+dFire, 1+dWind, 1+dEarth, 1+dWater,
        Math.max(0.2,1-travel), 1+prod, 1+loot, Math.max(0.2,1-train), Math.max(0.2,1-navalTrain),
        1+storage, 1+specialProd, Math.max(0.2,1-build), 1+lootStolen, Math.min(0.9,lootProtect), 1+siegeWall, flags);
  }

  // --- point economy ---------------------------------------------------------

  private int effectiveCost(City c, LibraryTree.Research def){
    int cost = def.pointCost();
    Race race = c.getRace();
    // Each race gets its signature unit one point cheaper (tier-1 nodes already cost 1 → no effect there).
    if (race==Race.GIANTS  && def.id().equals("warden"))   cost -= 1;   // stone brutes → Bastion tank
    if (race==Race.FAIRIES && def.id().equals("outrider")) cost -= 1;   // swift folk → Wild Hunt skirmisher
    if (race==Race.NEWTS   && def.id().equals("raider"))   cost -= 1;   // sea raiders → Warpath raider
    return Math.max(1, cost);
  }
  private int effectiveMinLevel(City c, LibraryTree.Research def){
    if (c.getRace()==Race.NEWTS && def.id().equals("raider")) return Math.max(0, def.minLibraryLevel()-1);
    return def.minLibraryLevel();
  }
  private int effectiveDuration(City c, LibraryTree.Research def){
    double lib = 1 - librarySpeedBonus(libraryLevel(c.getId()));  // higher Library level → faster research
    return (int)GameRules.fast(Math.max(5, Math.round(def.durationSeconds()*lib)));
  }

  /** Research-time reduction from the Library building level: 3% per level, capped at 60%. */
  public static double librarySpeedBonus(int libraryLevel){ return Math.min(0.60, Math.max(0, libraryLevel) * 0.03); }

  private int spentPoints(City c){
    int s=0;
    for (CityLibraryResearch cr : research.findByCityId(c.getId())){
      LibraryTree.Research def = LibraryTree.byId(cr.getResearchId());
      if (def!=null) s += effectiveCost(c, def);
    }
    return s;
  }

  /**
   * Tier-ladder gate: a tier-N node opens once ≥1 tier-(N−1) node in the <em>same branch</em> is
   * COMPLETED. Tier 1 is always open. No node-specific prerequisites — pick any path up a branch.
   */
  private static boolean prevTierDone(Collection<CityLibraryResearch> owned, LibraryTree.Research def){
    if (def.tier() <= 1) return true;
    return owned.stream()
        .filter(cr -> cr.getStatus()==CityLibraryResearch.Status.COMPLETED)
        .map(cr -> LibraryTree.byId(cr.getResearchId()))
        .filter(Objects::nonNull)
        .anyMatch(x -> x.branch()==def.branch() && x.tier()==def.tier()-1);
  }

  // --- actions ---------------------------------------------------------------

  @Transactional
  public void startResearch(Long playerId, Long cityId, String researchId){
    City c = ownedSynced(playerId, cityId);
    LibraryTree.Research def = LibraryTree.byId(researchId);
    if (def==null) throw new IllegalArgumentException("Unknown research");
    List<CityLibraryResearch> owned = research.findByCityId(cityId);
    Map<String,CityLibraryResearch> byId = new HashMap<>();
    for (CityLibraryResearch cr : owned) byId.put(cr.getResearchId(), cr);
    if (byId.containsKey(researchId)) throw new IllegalStateException("Already researched or in progress");
    if (owned.stream().anyMatch(cr -> cr.getStatus()==CityLibraryResearch.Status.RESEARCHING))
      throw new IllegalStateException("Another research is already in progress");
    if (libraryLevel(cityId) < effectiveMinLevel(c, def))
      throw new IllegalStateException("Requires Library level " + effectiveMinLevel(c, def));
    if (!prevTierDone(owned, def))
      throw new IllegalStateException("Requires any tier-" + (def.tier()-1) + " research in this branch");
    int available = libraryLevel(cityId)*LibraryTree.POINTS_PER_LEVEL - spentPoints(c);
    int cost = effectiveCost(c, def);
    if (available < cost) throw new IllegalStateException("Not enough research points");

    CityLibraryResearch cr = new CityLibraryResearch(cityId, researchId);
    cr.setStatus(CityLibraryResearch.Status.RESEARCHING);
    cr.setStartedAt(Instant.now());
    cr.setCompletesAt(Instant.now().plusSeconds(effectiveDuration(c, def)));
    research.save(cr);
  }

  @Transactional
  public void respec(Long playerId, Long cityId){
    City c = ownedSynced(playerId, cityId);
    int count = research.findByCityId(cityId).size();
    if (count == 0) throw new IllegalStateException("No research to reset");
    long cost = 200L * count;   // resource cost scales with researches refunded
    if (c.getWood()<cost || c.getStone()<cost || c.getWheat()<cost)
      throw new IllegalStateException("Re-spec costs " + cost + " of each resource");
    c.setWood(c.getWood()-cost); c.setStone(c.getStone()-cost); c.setWheat(c.getWheat()-cost);
    cities.save(c);
    research.deleteByCityId(cityId);
  }

  // --- read views ------------------------------------------------------------

  @Transactional
  public Map<String,Object> library(Long playerId, Long cityId){
    City c = ownedSynced(playerId, cityId);
    int level = libraryLevel(cityId);
    int total = level*LibraryTree.POINTS_PER_LEVEL;
    int spent = spentPoints(c);
    Map<String,CityLibraryResearch> owned = new HashMap<>();
    for (CityLibraryResearch cr : research.findByCityId(cityId)) owned.put(cr.getResearchId(), cr);
    boolean anyResearching = owned.values().stream().anyMatch(cr -> cr.getStatus()==CityLibraryResearch.Status.RESEARCHING);

    List<Map<String,Object>> tree = new ArrayList<>();
    for (LibraryTree.Research def : LibraryTree.ALL){
      CityLibraryResearch cr = owned.get(def.id());
      String state = cr==null ? "LOCKED"
          : cr.getStatus()==CityLibraryResearch.Status.RESEARCHING ? "RESEARCHING" : "COMPLETED";
      boolean tierOk = prevTierDone(owned.values(), def);
      int cost = effectiveCost(c, def);
      boolean available = state.equals("LOCKED") && tierOk
          && level >= effectiveMinLevel(c, def) && !anyResearching && (total-spent) >= cost;
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("id", def.id()); m.put("branch", def.branch().name()); m.put("tier", def.tier());
      m.put("name", def.name()); m.put("effect", def.effectText());
      m.put("pointCost", cost); m.put("durationSeconds", effectiveDuration(c, def));
      m.put("minLibraryLevel", effectiveMinLevel(c, def)); m.put("tierOk", tierOk);
      m.put("state", state); m.put("available", available);
      if (cr!=null && cr.getStatus()==CityLibraryResearch.Status.RESEARCHING)
        m.put("completesAt", cr.getCompletesAt()==null?null:cr.getCompletesAt().toString());
      tree.add(m);
    }

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("level", level); out.put("maxLevel", LibraryTree.MAX_LEVEL);
    out.put("totalPoints", total); out.put("spentPoints", spent); out.put("availablePoints", total-spent);
    out.put("fullTreeCost", LibraryTree.totalCost());
    out.put("race", c.getRace()==null?null:c.getRace().name());
    out.put("raceAffinity", affinityText(c.getRace()));
    out.put("tree", tree);
    return out;
  }

  @Transactional
  public Map<String,Object> activeBonuses(Long playerId, Long cityId){
    City c = ownedSynced(playerId, cityId);
    LibEffects fx = effects(cityId);
    List<String> lines = new ArrayList<>();
    if (c.getRace()!=null) for (var e : c.getRace().bonusesPct().entrySet())
      if (e.getValue()!=0) lines.add("Race " + e.getKey() + " " + (e.getValue()>0?"+":"") + e.getValue() + "%");
    for (CityLibraryResearch cr : research.findByCityId(cityId))
      if (cr.getStatus()==CityLibraryResearch.Status.COMPLETED){
        LibraryTree.Research def = LibraryTree.byId(cr.getResearchId());
        if (def!=null) lines.add(def.name() + " — " + def.effectText());
      }
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("race", c.getRace()==null?null:c.getRace().name());
    out.put("siegeEnabled", fx.has("siege"));
    out.put("dominionEnabled", fx.has("dominion"));
    out.put("cityGuardEnabled", fx.has("cityGuard"));
    out.put("cityGuardReadyAt", c.getCityGuardReadyAt()==null?null:c.getCityGuardReadyAt().toString());
    out.put("blitzEnabled", fx.has("blitz"));
    out.put("blitzReadyAt", c.getBlitzReadyAt()==null?null:c.getBlitzReadyAt().toString());
    out.put("lines", lines);
    return out;
  }

  private String affinityText(Race r){
    if (r==null) return null;
    return switch (r){
      case HUMANS -> null;
      case GIANTS -> "Giant might — the Warden costs 1 less point";
      case FAIRIES -> "Fairy wit — the Outrider costs 1 less point";
      case NEWTS -> "Newt cunning — the Raider costs 1 less and unlocks a level earlier";
    };
  }

  private City ownedSynced(Long playerId, Long cityId){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    return c;
  }
}
