package com.polis.domain;

/**
 * Active hero skills. Each unlocks at a level and is armed before an action, consuming a
 * cooldown. Effects are applied by the combat/travel code when the skill is armed.
 */
public enum HeroSkill {
  CHARGE(5, 12),        // +25% attack power on the next offensive battle
  PHALANX(10, 12),      // +30% SHARP defence on the next defence of its city
  FORCED_MARCH(15, 24), // next movement ignores 40% of travel time
  WAR_CRY(20, 24);      // no losses in the 1st round of the next fight

  public final int unlockLevel;
  public final int cooldownHours;
  HeroSkill(int unlockLevel, int cooldownHours){ this.unlockLevel = unlockLevel; this.cooldownHours = cooldownHours; }
}
