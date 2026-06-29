package com.polis.game;

import com.polis.domain.BuildingType;
import com.polis.domain.UnitType;
import java.util.Map;

/** Pure functions for the game economy. No state, no Spring — trivially unit-testable. */
public final class GameRules {
  private GameRules(){}

  // ~75 pop per Farm level → 3000 at max (level 40); buildings cost ~1000 pop maxed, leaving ~2000 free.
  public static int farmPop(int level){ return level<=0 ? 0 : 75*level; }
  // Warehouse storage cap scales with the WAREHOUSE building level: 1000 at level 0 up to
  // 20,000 at the level-25 max (smooth 20x curve). This is the per-resource cap.
  public static long storeCap(int level){ return level<=0 ? 1000L : Math.round(1000*Math.pow(20, level/25.0)); }
  public static double prodPerHour(int level){ return level<=0 ? 5 : Math.round(28*Math.pow(1.21, level-1)); }

  // Build-cost growth is tied to the warehouse-cap curve (same 20^(1/25) per-level factor) so
  // every upgrade stays affordable: every building's base cost is < 1000 (the level-0 cap), so
  // cost(level) < storeCap(level) at matching warehouse level, and the priciest upgrade stays
  // under the 20,000 max storage. The per-building `mul` is no longer used for cost growth —
  // relative expense between buildings comes from their base wood/stone/wheat values.
  private static final double COST_GROWTH = Math.pow(20, 1/25.0);  // ≈1.1273, matches storeCap()
  public static long[] buildCost(BuildingType b, int level){
    double m=Math.pow(COST_GROWTH, level);
    return new long[]{ Math.round(b.baseWood*m), Math.round(b.baseStone*m), Math.round(b.baseWheat*m) };
  }
  // Senate cuts build time 2.5%/level up to 75% — reached at the level-30 Senate cap, so every
  // upgrade keeps improving construction speed (the old 4%/level capped out at 60% by level 15).
  public static int buildSeconds(BuildingType b, int level, int senateLevel){
    double t=b.baseTime*Math.pow(1.28, level);
    double speed=1-Math.min(0.75, senateLevel*0.025);
    return (int)Math.max(3, Math.round(t*speed));
  }
  // Training time: 3%/level faster, up to 50%. Counted from the building's own level (not level-1)
  // so a level-1 Barracks/Harbor already trains a touch faster than nothing — it trains, just slowly.
  public static int unitSeconds(UnitType u, int fromBuildingLevel){
    return (int)Math.max(3, Math.round(u.getTrainSeconds()*(1-Math.min(0.5, fromBuildingLevel*0.03))));
  }
  public static int buildingPoints(int level){ return level*(level+1)/2; }
  public static int cityPoints(Map<BuildingType,Integer> levels){
    int p=0; for(int lv: levels.values()) p+=buildingPoints(lv); return p;
  }
  public static int levelReq(int level){ return 60+level*60; }
  public static int citySlots(int level){ return level+1; }

  // ---- city-expansion progression: Culture Points → levels → city slots (cap 20) ----
  public static final int MAX_LEVEL = 20;
  /** Max cities a player may hold = their level, hard-capped at 20. */
  public static int maxCities(int level){ return Math.min(MAX_LEVEL, level); }
  // Culture Points to go FROM level (target-1) TO `target`. Tuned ~5 easy / 10 medium / 20 hard.
  private static final int[] CULTURE = {0,0,2,3,4,5,7,9,12,15,18,22,27,33,40,48,58,70,85,100,120};
  /** Culture Points needed to reach {@code target} level (target 2..20); MAX at the cap. */
  public static int cultureForLevel(int target){
    if (target < 2) return 0;
    if (target > MAX_LEVEL) return Integer.MAX_VALUE;
    return CULTURE[target];
  }
  // Festival config (tunable): both festivals yield 1 Culture Point; only the fuel differs.
  public static final long FESTIVAL_RESOURCE_COST = 15_000;   // of EACH base resource
  public static final int  FESTIVAL_COMBAT_COST   = 200;      // Combat Points
  public static final int  FESTIVAL_CULTURE_REWARD = 1;
  public static final int  FESTIVAL_SECONDS = 300;            // short duration; resolver completes it

  /** Fixed number of city plots on every island. */
  public static final int SLOTS_PER_ISLAND = 12;

  // ---- espionage (Watchtower) ----
  /** Spy mission cost (each base resource) and resolve delay. */
  public static final long SPY_RESOURCE_COST = 2_000;
  public static final int  SPY_SECONDS = 120;
  /** Chance a spy launched from a Watchtower of this level succeeds (before the defender's catch chance). */
  public static double spySuccessChance(int level){ return Math.min(0.95, 0.30 + level * 0.0325); }   // L0 .30 → L20 .95
  /** Chance a Watchtower of this level catches an enemy spy targeting the city. */
  public static double spyDefenseChance(int level){ return Math.min(0.85, 0.05 + level * 0.040); }     // L0 .05 → L20 .85
  /** Net success probability of attacker(spy) vs defender(catch). */
  public static double effectiveSpyChance(int attackerLevel, int defenderLevel){
    return Math.max(0.02, Math.min(0.98, spySuccessChance(attackerLevel) * (1 - spyDefenseChance(defenderLevel))));
  }

  // ---- world geometry: a disc with the Heart (Wonders) at the centre, player islands in 3 tiers ----
  // Radii sized so the rings hold 20 / 40 / 60 islands (tier 1/2/3) at MIN_GAP spacing — a
  // ~1440-plot world (120 islands × 12 slots).
  public static final int WORLD_CENTER_X = 2900, WORLD_CENTER_Y = 2900;
  /** Inner/outer radius of each tier band (px). Tier 1 hugs the Heart, tier 3 is the outer rim. */
  public static final int[] TIER_INNER = { 0, 600, 1150, 1900 };   // index = tier (1..3)
  public static final int[] TIER_OUTER = { 0, 1000, 1750, 2800 };
  /** Radius of the Tier 2 / Tier 3 boundary circle — the Colossus roams along this ring. */
  public static final int COLOSSUS_RING_RADIUS = (1750 + 1900) / 2;   // 1825
  public static final int HEART_RADIUS = 360;                    // Wonder Islands sit within this

  // ---- endgame: Wonders of the Aegean -------------------------------------------------------
  /** Wonders raise from 0 to this level; all three at max + held = a world win. */
  public static final int WONDER_MAX_LEVEL = 10;
  /** Endgame begins when the world reaches this many player-held cities (or {@link #ENDGAME_DAYS}). */
  public static final int ENDGAME_CITY_THRESHOLD = 1000;
  /** ...or this many days after world creation, whichever comes first. */
  public static final int ENDGAME_DAYS = 21;
  /** Consolidation: one alliance must hold all three maxed Wonders uninterrupted for this long. */
  public static final long CONSOLIDATION_SECONDS = 48L * 3600L;
  /** Losing a Wonder battle drops its level by this much (min 0) and resets consolidation. */
  public static final int WONDER_REGRESS_PER_LOSS = 1;
  /** Resources (of EACH type) the controlling alliance must pool to raise a Wonder to {@code target}. */
  public static long wonderLevelCost(int target){
    if (target < 1 || target > WONDER_MAX_LEVEL) return Long.MAX_VALUE;
    return 40_000L * target;   // 40k each → 400k at the final level; pooled across the alliance
  }
}
