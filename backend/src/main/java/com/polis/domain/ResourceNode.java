package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * A contested map location that generates resources while a player garrisons it. Production
 * accrues lazily (settle on read / attack / sweep), is delivered to the controller's alliance
 * treasury, and may drop a {@link HeroItem}. Held with troops, so guarding is an allocation
 * trade-off against defending or attacking with those same units.
 */
@Entity @Table(name="resource_nodes")
@Getter @Setter @NoArgsConstructor
public class ResourceNode {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Column(name="island_id", nullable=false) private Long islandId;
  private int x;
  private int y;
  @Enumerated(EnumType.STRING) @Column(name="node_type", nullable=false) private NodeType nodeType;
  private int level = 1;                                   // 1..5
  @Enumerated(EnumType.STRING) @Column(nullable=false) private NodeStatus status = NodeStatus.UNCLAIMED;

  @Column(name="controlling_player_id")   private Long controllingPlayerId;
  @Column(name="controlling_alliance_id") private Long controllingAllianceId;   // snapshot at claim time

  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json")
  private Map<String,Integer> garrison = new HashMap<>();   // unit name -> quantity

  @Column(name="accumulated_resources") private long accumulatedResources = 0;
  @Column(name="last_tick_at") private Instant lastTickAt = Instant.now();
  @Column(name="claimed_at") private Instant claimedAt;
  @Column(name="contested_until") private Instant contestedUntil;
}
