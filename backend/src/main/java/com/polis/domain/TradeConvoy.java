package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Goods physically travelling from a seller's city to the buyer's chosen delivery city after a
 * resource trade fills. Reuses the same travel mechanics as troop movements (terrain-aware, with
 * an abstracted sea leg) but always arrives safely — convoys cannot be intercepted or looted.
 * Capacity per convoy comes from the destination Market building level, not physical carts.
 */
@Entity @Table(name="trade_convoys")
@Getter @Setter @NoArgsConstructor
public class TradeConvoy {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Column(name="buyer_player_id", nullable=false) private Long buyerPlayerId;
  @Column(name="seller_player_id") private Long sellerPlayerId;
  @Column(name="origin_city_id", nullable=false) private Long originCityId;
  @Column(name="destination_city_id", nullable=false) private Long destinationCityId;

  /** Resource cargo: { "WOOD"|"STONE"|"SILVER" : quantity }. */
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb")
  private Map<String,Long> cargo = new HashMap<>();

  @Enumerated(EnumType.STRING) private ConvoyStatus status = ConvoyStatus.PENDING;
  /** Null while PENDING (waiting for a convoy slot); set on dispatch. */
  @Column(name="depart_at") private Instant departAt;
  @Column(name="arrive_at") private Instant arriveAt;
  /** Buyer has seen the delivery notification. */
  private boolean seen = false;
  @Column(name="created_at") private Instant createdAt = Instant.now();
}
