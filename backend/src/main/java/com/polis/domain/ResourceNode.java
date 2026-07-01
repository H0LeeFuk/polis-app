package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * A contested resource building on a resource island — a siege-style control point. Players garrison
 * it with troops; while controlled it generates resources every payout cycle, auto-delivered to the
 * controlling players' cities split by each player's troop share. Allies (same alliance as the
 * controller) may SUPPORT (reinforce and share the payout); enemies must ATTACK to seize it (the
 * winner's troops become the new garrison and their alliance takes control).
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

  @Column(name="controlling_player_id")   private Long controllingPlayerId;   // the player who first took it
  @Column(name="origin_city_id")          private Long originCityId;
  @Column(name="controlling_alliance_id") private Long controllingAllianceId; // the alliance that currently holds it

  /** Per-player garrison: playerId (as String) -> (unit name -> quantity). All stacks defend together;
   *  the payout is split by each player's share of total garrison population. */
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json")
  private Map<String,Map<String,Integer>> garrison = new HashMap<>();

  @Column(name="accumulated_resources") private long accumulatedResources = 0;   // legacy, unused now
  @Column(name="last_tick_at") private Instant lastTickAt = Instant.now();
  @Column(name="last_payout_at") private Instant lastPayoutAt;                    // 10-min payout cycle
  @Column(name="control_since") private Instant controlSince;                     // when the current alliance took it
  @Column(name="claimed_at") private Instant claimedAt;
  @Column(name="contested_until") private Instant contestedUntil;
}
