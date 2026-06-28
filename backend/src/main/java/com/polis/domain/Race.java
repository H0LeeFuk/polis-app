package com.polis.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The four playable city races (PART: Founding Cities). Race is chosen per city when it is
 * founded and is permanent. Unlike a runtime-editable table this is seeded config in code:
 * each race carries distinct passive {@code cityBonuses} (production / attack / defence /
 * travel / loot multipliers) and owns a distinct unit roster (see {@code unit_types.race}).
 *
 * <p>Multipliers are applied where the relevant system runs: production in
 * {@code CityService.sync}, attack/defence in {@code CombatEngine} via {@code TickScheduler},
 * travel in march dispatch, loot when plundering, and research cost in {@code BuildService}.
 */
public enum Race {
  // Races are pure cosmetic identity now — no passive bonuses. All multipliers are neutral (1.0).
  HUMANS("Humans",  "Balanced and adaptable settlers of the Aegean.",  "🏛", 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
  GIANTS("Giants",  "Towering brutes who raise cities of stone.",       "🗿", 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
  FAIRIES("Fairies","Swift and graceful folk of the glades.",           "🧚", 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
  NEWTS("Newts",    "Amphibious raiders at home on the open sea.",      "🦎", 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);

  public final String displayName;
  public final String description;
  public final String icon;
  /** Multiplier on resource production (>1 faster economy). */
  public final double prodMult;
  /** Multiplier on this city's army attack power. */
  public final double attackMult;
  /** Multiplier on this city's defensive power when raided. */
  public final double defenseMult;
  /** Multiplier on march time for armies leaving this city (<1 faster). */
  public final double travelMult;
  /** Multiplier on plunder carried home. */
  public final double lootMult;
  /** Multiplier on Library research cost (<1 cheaper). */
  public final double researchCostMult;

  Race(String displayName, String description, String icon,
       double prodMult, double attackMult, double defenseMult,
       double travelMult, double lootMult, double researchCostMult){
    this.displayName = displayName; this.description = description; this.icon = icon;
    this.prodMult = prodMult; this.attackMult = attackMult; this.defenseMult = defenseMult;
    this.travelMult = travelMult; this.lootMult = lootMult; this.researchCostMult = researchCostMult;
  }

  /** Races carry no passive bonuses — always empty. */
  public Map<String,Integer> bonusesPct(){ return new LinkedHashMap<>(); }

  public Map<String,Object> dto(){
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", name());
    m.put("name", displayName);
    m.put("description", description);
    m.put("icon", icon);
    m.put("bonuses", bonusesPct());
    return m;
  }
}
