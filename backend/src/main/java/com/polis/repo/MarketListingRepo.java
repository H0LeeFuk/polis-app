package com.polis.repo;

import com.polis.domain.ListingStatus;
import com.polis.domain.MarketListing;
import com.polis.domain.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MarketListingRepo extends JpaRepository<MarketListing, Long> {
  /** Active listings for a resource, cheapest first (then oldest) — the fill order for a buy. */
  List<MarketListing> findByResourceTypeAndStatusOrderByPricePerBundleAscCreatedAtAsc(ResourceType resourceType, ListingStatus status);
  List<MarketListing> findByStatusOrderByPricePerBundleAscCreatedAtAsc(ListingStatus status);
  List<MarketListing> findBySellerPlayerIdAndStatus(Long sellerPlayerId, ListingStatus status);
}
