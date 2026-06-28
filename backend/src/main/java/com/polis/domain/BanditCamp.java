package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Per-player, per-island bandit camp with 10 escalating levels; respawns at level 1 after a cooldown. */
@Entity @Table(name="bandit_camps")
@Getter @Setter @NoArgsConstructor
public class BanditCamp {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="island_id", nullable=false) private Long islandId;
  @Column(name="player_id", nullable=false) private Long playerId;
  @Column(name="current_level") private int currentLevel = 1;
  @Column(name="defeated_at") private Instant defeatedAt;   // set when level 10 is cleared
  @Column(name="respawn_at")  private Instant respawnAt;    // when it returns to level 1
  private Instant createdAt = Instant.now();
}
