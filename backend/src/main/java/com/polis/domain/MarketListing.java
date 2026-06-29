package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * A player's standing offer to sell resources for gold. Resources are escrowed out of the
 * seller's city the moment the listing is created; when a buyer fills it the seller is paid
 * gold instantly and the goods travel to the buyer as a {@link TradeConvoy}.
 */
@Entity @Table(name="market_listings")
@Getter @Setter @NoArgsConstructor
public class MarketListing {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Column(name="seller_player_id", nullable=false) private Long sellerPlayerId;
  @Column(name="source_city_id", nullable=false) private Long sourceCityId;
  @Enumerated(EnumType.STRING) @Column(name="resource_type", nullable=false) private ResourceType resourceType;
  /** Bundles still available (1 bundle = TradeService.BUNDLE_SIZE units). */
  private int bundles;
  @Column(name="price_per_bundle", nullable=false) private int pricePerBundle;
  @Enumerated(EnumType.STRING) private ListingStatus status = ListingStatus.ACTIVE;
  @Column(name="created_at") private Instant createdAt = Instant.now();
}
