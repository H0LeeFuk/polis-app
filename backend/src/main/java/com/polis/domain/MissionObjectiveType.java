package com.polis.domain;

/**
 * What a mission tracks. COUNT objectives accumulate (+amount); LEVEL objectives record the
 * highest value reached (max). CHAIN_COMPLETE auto-completes when its prerequisites are done.
 */
public enum MissionObjectiveType {
  BUILD_BUILDING(true),
  UPGRADE_BUILDING_LEVEL(false),
  TRAIN_TROOPS(true),
  ATTACK_PLAYER(true),
  ATTACK_BANDIT_CAMP(true),
  FOUND_CITY(true),
  OCCUPY_NODE(true),
  RESEARCH_COMPLETE(true),
  REACH_ACADEMY_LEVEL(false),
  JOIN_ALLIANCE(true),
  CHAIN_COMPLETE(true);

  /** true = accumulate progress; false = track the max level reached. */
  public final boolean countBased;
  MissionObjectiveType(boolean countBased){ this.countBased = countBased; }
}
