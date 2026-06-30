package com.polis.game;

import com.polis.domain.BuildingType;
import com.polis.domain.UnitType;
import java.util.Map;

/** Pure functions for the game economy. No state, no Spring — trivially unit-testable. */
public final class GameRules {
  private GameRules(){}

  /**
   * Global time multiplier for TEST/dev: set env {@code POLIS_TIME_SCALE} below 1 to make everything
   * (builds, training, marches, research, rituals, sieges, hero assignment) resolve faster. 1.0 = normal.
   * e.g. 0.02 ≈ 50× faster. Applied at every player-facing duration source.
   */
  public static final double TIME_SCALE = readTimeScale();
  private static double readTimeScale(){
    try { String s = System.getenv("POLIS_TIME_SCALE"); return (s==null||s.isBlank()) ? 1.0 : Math.max(0.0001, Double.parseDouble(s)); }
    catch (Exception e){ return 1.0; }
  }
  /** Apply TIME_SCALE to a duration in seconds (min 1s). */
  public static long fast(long seconds){ return Math.max(1, Math.round(seconds * TIME_SCALE)); }

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
    return (int)Math.max(1, fast(Math.max(3, Math.round(t*speed))));
  }
  // Training time: 3%/level faster, up to 50%. Counted from the building's own level (not level-1)
  // so a level-1 Barracks/Harbor already trains a touch faster than nothing — it trains, just slowly.
  public static int unitSeconds(UnitType u, int fromBuildingLevel){
    return (int)Math.max(1, fast(Math.max(3, Math.round(u.getTrainSeconds()*(1-Math.min(0.5, fromBuildingLevel*0.03))))));
  }
  // ---- city points: a fully-upgraded city is worth ~MAX_CITY_POINTS, end-loaded so the last levels
  // are worth far more than the first. Per-level increment grows with level² (a building at level L
  // contributes sum_{n=1..L} n² = L(L+1)(2L+1)/6 "raw" points), then everything is scaled so the sum
  // across ALL buildings at their max levels equals MAX_CITY_POINTS. The scale is derived from the
  // live BuildingType maxes, so it self-corrects if the catalog changes.
  public static final int MAX_CITY_POINTS = 15000;
  private static final double POINT_SCALE;
  static {
    double raw = 0;
    for (BuildingType b : BuildingType.values()) raw += rawBuildingPoints(b.max);
    POINT_SCALE = raw > 0 ? MAX_CITY_POINTS / raw : 1.0;
  }
  /** Unscaled point mass of a building at {@code level} = sum of n² for n=1..level (level²-weighted). */
  private static long rawBuildingPoints(int level){
    long L = Math.max(0, level);
    return L * (L + 1) * (2 * L + 1) / 6;
  }
  public static int buildingPoints(int level){ return (int)Math.round(rawBuildingPoints(level) * POINT_SCALE); }
  public static int cityPoints(Map<BuildingType,Integer> levels){
    int p=0; for(int lv: levels.values()) p+=buildingPoints(lv);
    return Math.min(p, MAX_CITY_POINTS);   // a fully-upgraded city is exactly 15000 (no rounding overshoot)
  }
  /**
   * Anti-farm: stomping a much weaker target still earns 1 Combat Point per kill, but your army
   * "fights weaker" against the helpless and takes disproportionate losses. Driven by the battle's
   * {@code globalRatio} (attacker power / defender power, weighted per element): a close fight
   * (≈1) → no penalty; the more lopsided your win, the heavier your own casualties.
   * Returns the EXTRA attacker-loss multiplier (≥1), capped at 3×.
   */
  public static double weakTargetLossMult(double globalRatio){
    if (globalRatio <= 1.2) return 1.0;                          // close fight: no penalty
    return Math.min(3.0, 1.0 + (globalRatio - 1.2) * 0.4);       // lopsided win → up to 3× your losses
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
  public static final int  FESTIVAL_SECONDS = 3 * 3600;       // 3h base; reduced 1%/Altar level (see altarRitualSeconds)
  /** Ritual duration after the Altar building's speed bonus: −1% per Altar level (max level 20 → −20%). */
  public static int altarRitualSeconds(int altarLevel){
    int lv = Math.max(0, Math.min(20, altarLevel));
    return (int)fast(Math.round(FESTIVAL_SECONDS * (1 - 0.01 * lv)));
  }

  /** Fixed number of city plots on every island. */
  public static final int SLOTS_PER_ISLAND = 12;

  // ---- espionage (Watchtower) ----
  /** Spy mission cost (each base resource) and resolve delay. */
  public static final long SPY_RESOURCE_COST = 2_000;
  public static final int  SPY_SECONDS = 120;
  /** A spy is a lone fast scout: its travel is paced by distance like an army, at this scout pace. */
  public static final int  SPY_MINUTES_PER_TILE = 6;
  /** Floor on a spy mission's resolve time regardless of how close the target is. */
  public static final int  SPY_MIN_SECONDS = 30;
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
