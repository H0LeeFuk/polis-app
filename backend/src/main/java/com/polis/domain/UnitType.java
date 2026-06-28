package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Seeded catalog of trainable units (table {@code unit_types}, populated by Flyway and
 * not runtime-editable). Defence is split into three types so no unit counters everything;
 * {@code attackType} decides which defence an attacker is matched against in combat.
 *
 * <p>Rows are looked up by {@link #name} via {@code UnitCatalog}; {@code city_units},
 * {@code build_jobs} and movement JSON all key troops by that uppercase name string.
 */
@Entity @Table(name="unit_types")
@Getter @Setter @NoArgsConstructor
public class UnitType {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(unique=true, nullable=false) private String name;

  @Enumerated(EnumType.STRING) @Column(name="attack_type", nullable=false) private AttackType attackType;
  private int attack;
  @Column(name="defense_blunt")    private int defenseBlunt;
  @Column(name="defense_sharp")    private int defenseSharp;
  @Column(name="defense_distance") private int defenseDistance;

  @Column(name="speed_minutes_per_tile") private int speedMinutesPerTile;  // higher = slower
  @Column(name="carry_capacity")         private int carryCapacity;
  @Column(name="population_cost")        private int populationCost;

  // --- training metadata (needed by the existing build/economy systems) ---
  @Enumerated(EnumType.STRING) private UnitKind kind;
  @Enumerated(EnumType.STRING) @Column(name="from_queue") private QueueType fromQueue;
  @Column(name="train_seconds") private int trainSeconds;
  @Column(name="cost_wood")   private int costWood;
  @Column(name="cost_stone")  private int costStone;
  @Column(name="cost_silver") private int costSilver;
  /** Name of a {@link ResearchType} required to train this unit, or null if always available. */
  @Column(name="research_required") private String researchRequired;

  /** Race that may train this unit. Null = shared/neutral roster trainable by any race. */
  @Enumerated(EnumType.STRING) private Race race;
}
