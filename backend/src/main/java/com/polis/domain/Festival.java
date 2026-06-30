package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * A running/completed Altar festival. Festivals are run from a city but their Culture Points
 * accrue to the PLAYER (account-wide progression). Completed by the scheduled resolver.
 */
@Entity @Table(name="festivals")
@Getter @Setter @NoArgsConstructor
public class Festival {
  public enum Type { FESTIVAL_OF_PLENTY, FESTIVAL_OF_TRIUMPH }
  public enum Fuel { RESOURCES, COMBAT_POINTS }
  public enum Status { RUNNING, COMPLETED }

  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="city_id", nullable=false) private Long cityId;
  @Column(name="player_id", nullable=false) private Long playerId;
  @Enumerated(EnumType.STRING) @Column(name="festival_type", nullable=false) private Type festivalType;
  @Enumerated(EnumType.STRING) @Column(name="fuel_type", nullable=false) private Fuel fuelType;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status = Status.RUNNING;
  @Column(name="started_at", nullable=false) private Instant startedAt = Instant.now();
  @Column(name="completes_at", nullable=false) private Instant completesAt;
  @Column(name="culture_points_reward", nullable=false) private int culturePointsReward;
}
