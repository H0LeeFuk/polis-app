package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * An 8-hour siege laid on a city. Started by a winning hero-led attack that brings at least one
 * Defense-role ship; the surviving land troops + ships + hero become the besieging force that must
 * hold BOTH layers for the full duration. Two independent break locks: if all besieging troops are
 * destroyed (land lock) OR all besieging ships are destroyed (sea lock), the siege breaks. If it
 * survives until {@link #endsAt}, the besieging player conquers the city.
 *
 * <p>Troop/ship maps are keyed by UPPERCASE unit name (same convention as Movement/Reinforcement).
 */
@Entity @Table(name="sieges")
@Getter @Setter @NoArgsConstructor
public class Siege {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  /** the besieged city */
  @Column(name="city_id", nullable=false) private Long cityId;
  @Column(name="besieging_player_id", nullable=false) private Long besiegingPlayerId;
  @Column(name="besieging_alliance_id") private Long besiegingAllianceId;
  /** city the besieging army marched from — where leftovers/the hero withdraw to */
  @Column(name="origin_city_id") private Long originCityId;
  @Enumerated(EnumType.STRING) private SiegeStatus status = SiegeStatus.ACTIVE;
  @Column(name="started_at") private Instant startedAt = Instant.now();
  @Column(name="ends_at") private Instant endsAt;

  /** land troops holding the siege (land lock breaks when these reach 0 combatants) */
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="besieging_troops", columnDefinition="json")
  private Map<String,Integer> besiegingTroops = new HashMap<>();
  /** ships holding the sea blockade (sea lock breaks when these reach 0 combatant ships) */
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="besieging_ships", columnDefinition="json")
  private Map<String,Integer> besiegingShips = new HashMap<>();

  /** the hero locked in the siege (counts toward the siege's defense); freed on conquest/break */
  @Column(name="hero_id") private Long heroId;
}
