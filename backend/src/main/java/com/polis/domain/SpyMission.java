package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** An in-flight spy mission launched from a Watchtower; resolved by the scheduler at {@code resolvesAt}. */
@Entity @Table(name="spy_missions")
@Getter @Setter @NoArgsConstructor
public class SpyMission {
  public enum Status { IN_PROGRESS, RESOLVED }

  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="spying_player_id", nullable=false) private Long spyingPlayerId;
  @Column(name="origin_city_id", nullable=false)   private Long originCityId;
  @Column(name="target_city_id", nullable=false)   private Long targetCityId;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status = Status.IN_PROGRESS;
  @Enumerated(EnumType.STRING) private SpyOutcome outcome;        // set on resolve
  @Column(name="started_at", nullable=false)  private Instant startedAt = Instant.now();
  @Column(name="resolves_at", nullable=false) private Instant resolvesAt;
}
