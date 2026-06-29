package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** A pending invitation for a player to join an alliance. */
@Entity @Table(name="alliance_invites")
@Getter @Setter @NoArgsConstructor
public class AllianceInvite {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="alliance_id", nullable=false) private Long allianceId;
  @Column(name="player_id", nullable=false) private Long playerId;
  @Column(name="invited_by") private Long invitedBy;
  @Column(name="created_at") private Instant createdAt = Instant.now();
}
