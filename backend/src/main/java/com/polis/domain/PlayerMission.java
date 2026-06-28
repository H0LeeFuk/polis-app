package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Per-player progress against a {@link Mission}. */
@Entity @Table(name="player_missions",
    uniqueConstraints=@UniqueConstraint(columnNames={"player_id","mission_id"}))
@Getter @Setter @NoArgsConstructor
public class PlayerMission {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="player_id", nullable=false) private Long playerId;
  @Column(name="mission_id", nullable=false) private Long missionId;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private PlayerMissionStatus status = PlayerMissionStatus.LOCKED;
  @Column(nullable=false) private int progress = 0;
  @Column(name="completed_at") private Instant completedAt;
}
