package com.polis.game;

/** Tunable constants for resource-node production and rare drops. */
public final class NodeRules {
  private NodeRules(){}

  /** Base resources/hour at full garrison, before the level multiplier. */
  public static final double BASE_RATE_PER_HOUR = 60.0;

  /** Garrison cap (in population) per node level — production scales with garrison up to this. */
  public static int garrisonCap(int level){ return level * 50; }

  /** Production/hour given level and effective garrison population. */
  public static double ratePerHour(int level, int effectiveGarrisonPop){
    int cap = garrisonCap(level);
    if (cap <= 0) return 0;
    return BASE_RATE_PER_HOUR * level * (Math.min(effectiveGarrisonPop, cap) / (double) cap);
  }

  // rare drop chance (fractions). Tune freely.
  public static final double BASE_DROP_CHANCE = 0.005;   // 0.5%
  public static final double PER_POP_BONUS    = 0.0002;  // +0.02% per guarding population
  public static final double MAX_DROP_CHANCE  = 0.05;    // 5%

  public static double dropChance(int guardingPop, double itemDropBonusPct){
    double c = BASE_DROP_CHANCE + guardingPop * PER_POP_BONUS + itemDropBonusPct;
    return Math.min(MAX_DROP_CHANCE, Math.max(0, c));
  }
}
