package com.polis.repo;

import com.polis.domain.ConvoyStatus;
import com.polis.domain.TradeConvoy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TradeConvoyRepo extends JpaRepository<TradeConvoy, Long> {
  /** Convoys in transit that have arrived — ready to deliver their cargo. */
  List<TradeConvoy> findByStatusAndArriveAtLessThanEqual(ConvoyStatus status, Instant now);
  List<TradeConvoy> findByStatusOrderByCreatedAtAsc(ConvoyStatus status);
  long countByDestinationCityIdAndStatus(Long destinationCityId, ConvoyStatus status);
  List<TradeConvoy> findByBuyerPlayerIdAndStatusIn(Long buyerPlayerId, Collection<ConvoyStatus> statuses);
  List<TradeConvoy> findByBuyerPlayerIdAndStatusAndSeenFalse(Long buyerPlayerId, ConvoyStatus status);
}
