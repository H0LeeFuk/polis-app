package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Accumulated damage one PLAYER has dealt to an island boss — the unit of reward distribution. */
@Entity @Table(name="island_boss_damage",
    uniqueConstraints=@UniqueConstraint(columnNames={"boss_id","player_id"}))
@Getter @Setter @NoArgsConstructor
public class IslandBossDamage {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="boss_id", nullable=false) private Long bossId;
  @Column(name="player_id", nullable=false) private Long playerId;
  @Column(name="accumulated_damage", nullable=false) private long accumulatedDamage = 0;
  @Column(name="last_contribution_at") private Instant lastContributionAt;

  public IslandBossDamage(Long bossId, Long playerId){ this.bossId=bossId; this.playerId=playerId; }
}
