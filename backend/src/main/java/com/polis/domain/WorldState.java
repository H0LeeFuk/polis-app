package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Per-world endgame tracker. Drives the GROWTH -> ENDGAME -> FINISHED phase machine and the
 * consolidation win timer (one alliance must hold all three maxed Wonders without interruption).
 */
@Entity @Table(name="world_state")
@Getter @Setter @NoArgsConstructor
public class WorldState {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private WorldPhase phase = WorldPhase.GROWTH;
  @Column(name="endgame_started_at") private Instant endgameStartedAt;
  // the consolidation countdown: set when one alliance first holds all 3 maxed Wonders, cleared if it slips
  @Column(name="consolidation_started_at") private Instant consolidationStartedAt;
  @Column(name="consolidation_alliance_id") private Long consolidationAllianceId;
  @Column(name="winner_alliance_id") private Long winnerAllianceId;
  @Column(name="finished_at") private Instant finishedAt;
}
