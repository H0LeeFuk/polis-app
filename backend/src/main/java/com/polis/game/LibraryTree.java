package com.polis.game;

import com.polis.domain.LibraryBranch;

import java.util.*;

/**
 * Seeded-in-code Library research tree (3 branches × 3 tiers). The point budget is deliberately
 * scarcer than the full tree cost (~80 points at Library 20 vs ~130 to buy everything), forcing
 * each city to specialize. Effects are accumulated into a per-city bundle by {@link LibraryService}.
 *
 * Buff stacking order documented everywhere combat math runs: race → Library → hero attrs → hero items.
 */
public final class LibraryTree {
  private LibraryTree(){}

  public static final int POINTS_PER_LEVEL = 4;
  public static final int MAX_LEVEL = 20;

  /** A research node. effects: additive percentages (0.05 = +5%); flags: boolean unlocks. */
  public record Research(String id, LibraryBranch branch, int tier, String name, String effectText,
                         int pointCost, int durationSeconds, int minLibraryLevel,
                         List<String> prereqs, boolean needsTwoTier2,
                         Map<String,Double> effects, Set<String> flags){}

  private static Research r(String id, LibraryBranch b, int tier, String name, String txt,
                            int cost, int dur, int minLv, List<String> pre, boolean two,
                            Map<String,Double> eff, String... flags){
    return new Research(id, b, tier, name, txt, cost, dur, minLv, pre, two, eff, Set.of(flags));
  }
  private static Map<String,Double> e(Object... kv){
    Map<String,Double> m = new LinkedHashMap<>();
    for (int i=0;i<kv.length;i+=2) m.put((String)kv[i], ((Number)kv[i+1]).doubleValue());
    return m;
  }
  private static final int D1=300, D2=900, D3=1800;

  // Costs sum to ~104 vs 80 points at Library 20 — you can master ~2 branches, never all three.
  public static final List<Research> ALL = List.of(
    // ⚔ WAR
    r("whetstones", LibraryBranch.WAR,1,"Whetstones","+5% attack (all troops)",2,D1,2,List.of(),false,e("attack",0.05)),
    r("drillmasters",LibraryBranch.WAR,1,"Drillmasters","−10% troop training time",2,D1,2,List.of(),false,e("trainTime",0.10)),
    r("beast_riders",LibraryBranch.WAR,2,"Beast Riders","+10% mounted attack",4,D2,0,List.of("whetstones"),false,e("attack",0.05)),
    r("keen_volleys",LibraryBranch.WAR,2,"Keen Volleys","+10% distance attack",4,D2,0,List.of("whetstones"),false,e("attack",0.05)),
    r("war_chants", LibraryBranch.WAR,2,"War Chants","Reduced 1st-round losses on attack",4,D2,0,List.of("drillmasters"),false,e(),"warChants"),
    r("bloodfury",  LibraryBranch.WAR,3,"Bloodfury","+15% attack, −5% defense",8,D3,10,List.of(),true,e("attack",0.15,"defense",-0.05)),
    r("plunderers_creed",LibraryBranch.WAR,3,"Plunderer's Creed","+20% loot on victories",8,D3,12,List.of("war_chants"),false,e("loot",0.20)),
    // 🛡 WARDS
    r("runed_shields",LibraryBranch.WARDS,1,"Runed Shields","+5% defense (all)",2,D1,2,List.of(),false,e("defense",0.05)),
    r("far_seers",  LibraryBranch.WARDS,1,"Far-Seers","Reveal incoming attack composition",2,D1,3,List.of(),false,e(),"farSeers"),
    r("stone_bulwark",LibraryBranch.WARDS,2,"Stone Bulwark","+12% Earth defense",4,D2,0,List.of("runed_shields"),false,e("defEarth",0.12)),
    r("ember_wards",LibraryBranch.WARDS,2,"Ember Wards","+12% Fire defense",4,D2,0,List.of("runed_shields"),false,e("defFire",0.12)),
    r("gale_wards",LibraryBranch.WARDS,2,"Gale Wards","+12% Wind defense",4,D2,0,List.of("far_seers"),false,e("defWind",0.12)),
    r("tide_wards",LibraryBranch.WARDS,2,"Tide Wards","+12% Water defense",4,D2,0,List.of("far_seers"),false,e("defWater",0.12)),
    r("aegis_ward", LibraryBranch.WARDS,3,"Aegis Ward","+15% defense (all)",8,D3,10,List.of(),true,e("defense",0.15)),
    r("everguard",  LibraryBranch.WARDS,3,"Everguard","Defenders recover part of losses",10,D3,14,List.of("aegis_ward"),false,e(),"recover"),
    // 📜 LORE & DOMINION
    r("wayfinding", LibraryBranch.LORE,1,"Wayfinding","−10% travel time",2,D1,2,List.of(),false,e("travel",0.10)),
    r("pack_trains",LibraryBranch.LORE,1,"Pack Trains","+15% loot capacity",2,D1,3,List.of(),false,e("loot",0.15)),
    r("tidecraft",  LibraryBranch.LORE,2,"Tidecraft","−15% naval travel time",4,D2,0,List.of("wayfinding"),false,e("navalTravel",0.15)),
    r("siegecraft", LibraryBranch.LORE,2,"Siegecraft","Unlocks siege engines",8,D2,8,List.of(),false,e(),"siege"),
    r("abundance",  LibraryBranch.LORE,2,"Abundance","+10% resource production",4,D2,0,List.of("pack_trains"),false,e("production",0.10)),
    r("dominion",   LibraryBranch.LORE,3,"Dominion","Enables conquering enemy cities",12,D3,15,List.of("siegecraft"),false,e(),"dominion"),
    r("grand_roads",LibraryBranch.LORE,3,"Grand Roads","−20% travel between your cities",6,D3,14,List.of("tidecraft"),false,e("cityTravel",0.20)),
    r("deepforges", LibraryBranch.LORE,2,"Deepforges","−15% naval unit training time",4,D2,0,List.of("tidecraft"),false,e("navalTrainSpeed",0.15))
  );

  private static final Map<String,Research> BY_ID = new LinkedHashMap<>();
  static { for (Research x : ALL) BY_ID.put(x.id(), x); }
  public static Research byId(String id){ return BY_ID.get(id); }
  public static int totalCost(){ int s=0; for (Research x: ALL) s+=x.pointCost(); return s; }
}
