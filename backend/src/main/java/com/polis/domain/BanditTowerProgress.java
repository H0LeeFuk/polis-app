package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * One row per player: their account-wide Bandit Tower climb (100 levels, forward-only).
 * {@code currentLevelDefenders} is the LIVE, possibly-damaged defending force of the current level —
 * survivors persist across attempts so repeated waves wear a level down until it is cleared.
 */
@Entity @Table(name="bandit_tower_progress")
@Getter @Setter @NoArgsConstructor
public class BanditTowerProgress {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="player_id", nullable=false, unique=true) private Long playerId;
  @Column(name="current_level", nullable=false) private int currentLevel = 1;
  @Column(name="highest_cleared", nullable=false) private int highestCleared = 0;

  /** Survivors of the current level's defending force (keyed by UPPERCASE unit name). */
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="current_level_defenders", columnDefinition="json")
  private Map<String,Integer> currentLevelDefenders = new HashMap<>();

  @Column(name="current_level_initialized", nullable=false) private boolean currentLevelInitialized = false;

  public BanditTowerProgress(Long playerId){ this.playerId = playerId; }
}
