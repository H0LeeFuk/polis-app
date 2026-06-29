package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Seeded catalog of trainable units (table {@code unit_types}, populated by Flyway and
 * not runtime-editable). Defence is split into the four ELEMENTS so no unit resists everything;
 * an attacker hits with its race's element ({@code attackElement}, or the attacking city's race
 * element for shared units) and is matched against the defender's resistance to that element.
 * Siege units ({@code isSiege}) deal wall damage outside the elemental troop formula.
 *
 * <p>Rows are looked up by {@link #name} via {@code UnitCatalog}; {@code city_units},
 * {@code build_jobs} and movement JSON all key troops by that uppercase name string.
 */
@Entity @Table(name="unit_types")
@Getter @Setter @NoArgsConstructor
public class UnitType {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(unique=true, nullable=false) private String name;

  private int attack;
  /** Element this unit attacks with; null = shared unit, resolved from the attacking city's race. */
  @Enumerated(EnumType.STRING) @Column(name="attack_element") private Element attackElement;
  /** Siege units bypass the elemental troop formula and damage walls/buildings instead. */
  @Column(name="is_siege") private boolean siege;

  @Column(name="defense_fire")  private int defenseFire;
  @Column(name="defense_wind")  private int defenseWind;
  @Column(name="defense_earth") private int defenseEarth;
  @Column(name="defense_water") private int defenseWater;

  @Column(name="speed_minutes_per_tile") private int speedMinutesPerTile;  // higher = slower
  @Column(name="carry_capacity")         private int carryCapacity;
  @Column(name="population_cost")        private int populationCost;

  // --- training metadata (needed by the existing build/economy systems) ---
  @Enumerated(EnumType.STRING) private UnitKind kind;
  @Enumerated(EnumType.STRING) @Column(name="from_queue") private QueueType fromQueue;
  @Column(name="train_seconds") private int trainSeconds;
  @Column(name="cost_wood")    private int costWood;
  @Column(name="cost_stone")   private int costStone;
  @Column(name="cost_wheat")   private int costWheat;
  /** Special-resource cost (>0 marks an ELITE unit; the resource is the city race's special). */
  @Column(name="cost_special") private int costSpecial;
  /** Name of a {@link ResearchType} required to train this unit, or null if always available. */
  @Column(name="research_required") private String researchRequired;

  /** Race that may train this unit. Null = shared/neutral roster trainable by any race. */
  @Enumerated(EnumType.STRING) private Race race;

  /** How this unit crosses the map (land/flying/swimming). Drives water-crossing + travel pace. */
  @Enumerated(EnumType.STRING) @Column(name="movement_class", nullable=false) private MovementClass movementClass = MovementClass.LAND;
  /** For transports: how much LAND population this unit can ferry across water (0 = not a transport). */
  @Column(name="transport_capacity") private int transportCapacity;

  /** Which battle layer this unit fights in. Ground troops = LAND; ships + Newt aquatic = SEA. */
  @Enumerated(EnumType.STRING) @Column(name="combat_layer", nullable=false) private CombatLayer combatLayer = CombatLayer.LAND;
  /** Ship role for LAND-race fleets (Transport/Defense/Attack); null for non-ships incl. Newt aquatic. */
  @Enumerated(EnumType.STRING) @Column(name="ship_role") private ShipRole shipRole;

  /** This unit's resistance to a given element. */
  @Transient public int defenseOf(Element e){
    return switch (e){ case FIRE -> defenseFire; case WIND -> defenseWind; case EARTH -> defenseEarth; case WATER -> defenseWater; };
  }
  /** True if this is an elite unit gated behind a special resource. */
  @Transient public boolean isElite(){ return costSpecial > 0; }
  /** LAND units need a transport to cross open water; flyers and swimmers do not. */
  @Transient public boolean isRequiresTransport(){ return movementClass == MovementClass.LAND; }
  @Transient public boolean isTransport(){ return transportCapacity > 0; }
  /** SEA-layer = ships and Newt aquatic units; LAND-layer = ground troops. */
  @Transient public boolean isSea(){ return combatLayer == CombatLayer.SEA; }
}
