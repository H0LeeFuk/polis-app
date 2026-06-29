package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * A Wonder of the Aegean: a militarily-held endgame objective (like a {@link ResourceNode}) that
 * an alliance raises from level 0 to 10 by pooling resources. Losing a battle drops its level and
 * resets the world consolidation timer. Holding all three at max level wins the world.
 */
@Entity @Table(name="wonders")
@Getter @Setter @NoArgsConstructor
public class Wonder {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Column(name="island_id", nullable=false) private Long islandId;
  private String name;
  @Enumerated(EnumType.STRING) @Column(name="wonder_kind", nullable=false) private WonderKind wonderKind;
  private int x = 50;
  private int y = 50;
  private int level = 0;                                   // 0..10
  @Enumerated(EnumType.STRING) @Column(nullable=false) private WonderStatus status = WonderStatus.DORMANT;

  @Column(name="controlling_player_id")   private Long controllingPlayerId;
  @Column(name="controlling_alliance_id") private Long controllingAllianceId;

  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb")
  private Map<String,Integer> garrison = new HashMap<>();   // unit name -> quantity

  // alliance-pooled investment toward the NEXT level (consumed on level-up)
  @Column(name="invested_wood")  private long investedWood = 0;
  @Column(name="invested_stone") private long investedStone = 0;
  @Column(name="invested_wheat") private long investedWheat = 0;

  @Column(name="claimed_at") private Instant claimedAt;
  @Column(name="contested_until") private Instant contestedUntil;
  @Column(name="last_tick_at") private Instant lastTickAt = Instant.now();
}
