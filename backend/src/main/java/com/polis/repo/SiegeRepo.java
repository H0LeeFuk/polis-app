package com.polis.repo;

import com.polis.domain.Siege;
import com.polis.domain.SiegeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SiegeRepo extends JpaRepository<Siege, Long> {
  Optional<Siege> findByCityIdAndStatus(Long cityId, SiegeStatus status);
  List<Siege> findByStatus(SiegeStatus status);
  List<Siege> findByStatusAndEndsAtLessThanEqual(SiegeStatus status, Instant now);
  List<Siege> findByBesiegingPlayerIdAndStatus(Long besiegingPlayerId, SiegeStatus status);
  List<Siege> findByBesiegingAllianceIdAndStatus(Long besiegingAllianceId, SiegeStatus status);
}
