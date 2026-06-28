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
  HUMANS("Humans",
      "Balanced and adaptable — no weaknesses, steady bonuses across the board and cheaper Academy research.",
      "🏛", 1.05, 1.05, 1.05, 1.00, 1.00, 0.85),
  GIANTS("Giants",
      "Brute force. Devastating attack and heavy armour, but ponderous on the march and hungry for population.",
      "🗿", 0.90, 1.30, 1.25, 1.25, 0.90, 1.00),
  FAIRIES("Fairies",
      "Swift and prosperous — fast marches, rich production and fat loot, but fragile in defence.",
      "🧚", 1.25, 0.95, 0.80, 0.70, 1.40, 1.00),
  NEWTS("Newts",
      "Amphibious raiders — fearsome navies and quick crossings, dependable but unexceptional on land.",
      "🦎", 1.05, 1.05, 1.05, 0.95, 1.05, 1.00);

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
  /** Multiplier on Academy research cost (<1 cheaper). */
  public final double researchCostMult;

  Race(String displayName, String description, String icon,
       double prodMult, double attackMult, double defenseMult,
       double travelMult, double lootMult, double researchCostMult){
    this.displayName = displayName; this.description = description; this.icon = icon;
    this.prodMult = prodMult; this.attackMult = attackMult; this.defenseMult = defenseMult;
    this.travelMult = travelMult; this.lootMult = lootMult; this.researchCostMult = researchCostMult;
  }

  /** Passive modifiers as signed percentages, for the client "active bonuses" panel. */
  public Map<String,Integer> bonusesPct(){
    Map<String,Integer> m = new LinkedHashMap<>();
    m.put("production", pct(prodMult));
    m.put("attack", pct(attackMult));
    m.put("defense", pct(defenseMult));
    m.put("travel", pct(1.0 / travelMult));   // shown as speed: faster march = positive
    m.put("loot", pct(lootMult));
    if (researchCostMult != 1.0) m.put("researchSpeed", pct(1.0 / researchCostMult));
    return m;
  }
  private static int pct(double mult){ return (int)Math.round((mult - 1.0) * 100); }

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
