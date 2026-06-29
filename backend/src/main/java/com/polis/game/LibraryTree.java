package com.polis.game;

import com.polis.domain.LibraryBranch;

import java.util.*;

/**
 * Seeded-in-code Library research tree — 3 branches × 10 researches (30 total). Each branch is a
 * balanced mix (a new unit, a unit upgrade, troop speed, storage, looting, defense, economy, a
 * signature mechanic) so no branch is strictly stronger; they differ in flavor, not raw power.
 *
 * The point budget ({@link #POINTS_PER_LEVEL} × {@link #MAX_LEVEL}) is deliberately scarcer than the
 * full tree cost, so each city specializes (master ~one branch, dabble in a second; never all three).
 *
 * <p>Branches: WAR = "The Warpath" (assault & conquest), WARDS = "The Bastion" (defense, resilience &
 * economy), LORE = "The Wild Hunt" (mobility, beasts & the hero). Effects are accumulated into a
 * per-city bundle by {@link LibraryService}. Buff stacking order documented everywhere combat math
 * runs: race → Library → hero attrs → hero items.
 */
public final class LibraryTree {
  private LibraryTree(){}

  // 2 points/level → 40 at Library 20. Full tree costs ~57, so a city can master ~one branch and
  // part of a second, never all three — specialization is forced.
  public static final int POINTS_PER_LEVEL = 2;
  public static final int MAX_LEVEL = 20;

  /** A research node. effects: additive percentages (0.05 = +5%); flags: boolean unlocks. */
  public record Research(String id, LibraryBranch branch, int tier, String name, String effectText,
                         int pointCost, int durationSeconds, int minLibraryLevel,
                         List<String> prereqs, boolean needsTwoTier2,
                         Map<String,Double> effects, Set<String> flags){}

  private static Research r(String id, LibraryBranch b, int tier, String name, String txt,
                            int cost, int minLv, List<String> pre,
                            Map<String,Double> eff, String... flags){
    return new Research(id, b, tier, name, txt, cost, durationFor(cost), minLv, pre, false, eff, Set.of(flags));
  }
  private static Map<String,Double> e(Object... kv){
    Map<String,Double> m = new LinkedHashMap<>();
    for (int i=0;i<kv.length;i+=2) m.put((String)kv[i], ((Number)kv[i+1]).doubleValue());
    return m;
  }
  /** Research time scales with point cost: cheap foundations are fast, signature researches are slow. */
  private static int durationFor(int cost){
    return switch (cost){ case 1 -> 300; case 2 -> 900; case 3 -> 1800; default -> 3600; };
  }

  public static final List<Research> ALL = List.of(
    // ─────────────────────────── BRANCH I — "The Warpath" (assault & conquest) ───────────────────
    r("whetstones",    LibraryBranch.WAR,1,"Whetstones","+5% attack (all troops)",                       1,2, List.of(),               e("attack",0.05)),
    r("drillmasters",  LibraryBranch.WAR,1,"Drillmasters","−12% troop training time",                    1,2, List.of(),               e("trainTime",0.12)),
    r("forced_march",  LibraryBranch.WAR,1,"Forced March","+12% troop movement speed",                   1,3, List.of(),               e("travel",0.12)),
    r("raider",        LibraryBranch.WAR,2,"Raider","Unlocks the Raider — a cheap, fast offensive unit", 2,3, List.of("whetstones"),   e(), "unlockRaider"),
    r("war_stores",    LibraryBranch.WAR,1,"War Stores","+25% resource storage capacity",                1,3, List.of(),               e("storage",0.25)),
    r("bloodlust",     LibraryBranch.WAR,2,"Bloodlust","Each consecutive win within 6h: +3% attack (cap +15%)",2,6, List.of("whetstones"), e(), "bloodlust"),
    r("honed_raiders", LibraryBranch.WAR,3,"Honed Raiders","Unit upgrade: +15% attack & +10% HP for the Raider",2,7, List.of("raider"),   e(), "honedRaiders"),
    r("pillage",       LibraryBranch.WAR,2,"Pillage","+25% resources stolen on a victorious attack",     2,7, List.of("war_stores"),  e("lootStolen",0.25)),
    r("breach_engines",LibraryBranch.WAR,3,"Breach Engines","Build rams & siege towers; +20% wall damage",3,9, List.of("bloodlust"),   e("siegeWall",0.20), "siege"),
    r("conquest",      LibraryBranch.WAR,3,"Conquest","Enables conquering enemy cities",                 4,14,List.of("breach_engines"),e(), "dominion"),

    // ─────────────────────────── BRANCH II — "The Bastion" (defense, resilience & economy) ───────
    r("runed_wards",     LibraryBranch.WARDS,1,"Runed Wards","+5% defense (all elements)",               1,2, List.of(),                 e("defense",0.05)),
    r("master_builders", LibraryBranch.WARDS,1,"Master Builders","−15% building construction time",      1,2, List.of(),                 e("buildTime",0.15)),
    r("quickstep",       LibraryBranch.WARDS,1,"Quickstep","+10% troop movement speed",                  1,3, List.of(),                 e("travel",0.10)),
    r("warden",          LibraryBranch.WARDS,2,"Warden","Unlocks the Warden — a tanky defensive unit",   2,4, List.of("runed_wards"),    e(), "unlockWarden"),
    r("deep_vaults",     LibraryBranch.WARDS,2,"Deep Vaults","+35% resource storage capacity",           2,4, List.of(),                 e("storage",0.35)),
    r("city_guard",      LibraryBranch.WARDS,2,"City Guard","Farm's \"Call the Guard\": summon farmer-militia every 5h",2,6, List.of("runed_wards"), e(), "cityGuard"),
    r("tempered_wardens",LibraryBranch.WARDS,3,"Tempered Wardens","Unit upgrade: +20% defense for the Warden",2,7, List.of("warden"),     e(), "temperedWardens"),
    r("hidden_granaries",LibraryBranch.WARDS,2,"Hidden Granaries","Protect 20% of your resources from being looted",2,7, List.of("deep_vaults"), e("lootProtect",0.20)),
    r("spiteful_walls",  LibraryBranch.WARDS,3,"Spiteful Walls","Attackers assaulting this city suffer +15% extra losses",3,10,List.of("city_guard"), e(), "spitefulWalls"),
    r("last_bastion",    LibraryBranch.WARDS,3,"Last Bastion","Garrison below 25% → +25% defense for the rest of the battle",4,13,List.of("spiteful_walls"), e(), "lastBastion"),

    // ─────────────────────────── BRANCH III — "The Wild Hunt" (mobility, beasts & the hero) ──────
    r("sharpened_claws", LibraryBranch.LORE,1,"Sharpened Claws","+5% attack (all troops)",               1,2, List.of(),                 e("attack",0.05)),
    r("deep_veins",      LibraryBranch.LORE,1,"Deep Veins","+12% special-resource production",           1,2, List.of(),                 e("specialProd",0.12)),
    r("wind_gait",       LibraryBranch.LORE,1,"Wind Gait","+15% troop movement speed (the fastest)",     1,3, List.of(),                 e("travel",0.15)),
    r("outrider",        LibraryBranch.LORE,2,"Outrider","Unlocks the Outrider — a fast mounted/flying skirmisher",2,3, List.of("sharpened_claws"), e(), "unlockOutrider"),
    r("burrow_stores",   LibraryBranch.LORE,1,"Burrow Stores","+25% resource storage capacity",          1,3, List.of(),                 e("storage",0.25)),
    r("pack_instincts",  LibraryBranch.LORE,3,"Pack Instincts","Unit upgrade: +12% attack & +12% speed for the Outrider",2,6, List.of("outrider"), e(), "packInstincts"),
    r("ambush",          LibraryBranch.LORE,2,"Ambush","+12% attack and +15% loot vs targets weaker than you",2,7, List.of("outrider"),  e(), "ambush"),
    r("plunderers_haul", LibraryBranch.LORE,2,"Plunderer's Haul","+20% loot carry capacity & +15% resources stolen",2,7, List.of("burrow_stores"), e("loot",0.20,"lootStolen",0.15)),
    r("blitz",           LibraryBranch.LORE,3,"Blitz","−30% travel time for the next attack, on a cooldown",3,10,List.of("wind_gait"),   e(), "blitz"),
    r("siegebreaker",    LibraryBranch.LORE,3,"Hero: Siegebreaker","Hero-led army: +40% wall damage, ignores 10% wall defense",4,13,List.of("blitz"), e(), "siegebreaker")
  );

  private static final Map<String,Research> BY_ID = new LinkedHashMap<>();
  static { for (Research x : ALL) BY_ID.put(x.id(), x); }
  public static Research byId(String id){ return BY_ID.get(id); }
  public static int totalCost(){ int s=0; for (Research x: ALL) s+=x.pointCost(); return s; }
}
