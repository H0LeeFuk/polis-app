package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** The spying player's intel result — full city snapshot on SUCCESS, empty on CAUGHT. */
@Entity @Table(name="spy_reports")
@Getter @Setter @NoArgsConstructor
public class SpyReport {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="owner_player_id", nullable=false) private Long ownerPlayerId;
  @Column(name="target_city_id", nullable=false)  private Long targetCityId;
  @Column(name="target_city_name") private String targetCityName;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private SpyOutcome outcome;

  @JdbcTypeCode(SqlTypes.JSON) @Column(name="revealed_troops")    private Map<String,Integer> revealedTroops;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="revealed_resources") private Map<String,Long>    revealedResources;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="revealed_buildings") private Map<String,Integer> revealedBuildings;

  @Column(name="captured_at", nullable=false) private Instant capturedAt = Instant.now();
  @Column(name="is_read", nullable=false) private boolean read = false;
}
