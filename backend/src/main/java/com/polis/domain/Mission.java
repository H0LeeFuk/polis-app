package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * Seeded mission config (table {@code missions}, not runtime-editable). Missions form ordered
 * chains; a mission unlocks once its {@code prerequisiteMissionId} is complete. The final
 * starter mission sets {@code unlocksHeroKey = CELINE}.
 */
@Entity @Table(name="missions")
@Getter @Setter @NoArgsConstructor
public class Mission {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false) private String chain = "STARTER";
  @Column(name="order_index", nullable=false) private int orderIndex;
  @Column(nullable=false) private String title;
  @Column(columnDefinition="text") private String description;

  @Enumerated(EnumType.STRING) @Column(name="objective_type", nullable=false)
  private MissionObjectiveType objectiveType;
  @Column(name="objective_target", nullable=false) private int objectiveTarget = 1;

  // objective_params column exists in the schema for future filters; not mapped (kept simple).
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json")
  private Map<String,Integer> rewards = new HashMap<>();

  @Column(name="prerequisite_mission_id") private Long prerequisiteMissionId;
  @Enumerated(EnumType.STRING) @Column(name="unlocks_hero_key") private HeroKey unlocksHeroKey;
}
