package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Defender-side notification raised only when a city CATCHES an enemy spy (success is silent). */
@Entity @Table(name="spy_alerts")
@Getter @Setter @NoArgsConstructor
public class SpyAlert {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="owner_player_id", nullable=false) private Long ownerPlayerId;   // the defender
  @Column(name="spying_player_id") private Long spyingPlayerId;
  @Column(name="spying_player_name") private String spyingPlayerName;
  @Column(name="target_city_id") private Long targetCityId;
  @Column(name="target_city_name") private String targetCityName;
  @Column(name="caught_at", nullable=false) private Instant caughtAt = Instant.now();
  @Column(name="is_read", nullable=false) private boolean read = false;
}
