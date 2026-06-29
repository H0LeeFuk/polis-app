package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Foreign troops stationed at a host city to defend it — the result of a SUPPORT movement arriving.
 * The troops remain OWNED by {@code ownerPlayerId} (an ally or the player's other city) but fight on
 * the host's behalf when it is raided. One row per (host city, owning player); the unit map is keyed
 * by UPPERCASE unit name to match the combat/garrison convention.
 */
@Entity @Table(name="reinforcements")
@Getter @Setter @NoArgsConstructor
public class Reinforcement {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id") private Long worldId;
  @Column(name="host_city_id", nullable=false) private Long hostCityId;
  @Column(name="owner_player_id", nullable=false) private Long ownerPlayerId;

  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json")
  private Map<String,Integer> units = new HashMap<>();

  @Column(name="created_at") private Instant createdAt = Instant.now();
}
