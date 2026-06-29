package com.polis.repo;

import com.polis.domain.AllianceInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface AllianceInviteRepo extends JpaRepository<AllianceInvite, Long> {
  List<AllianceInvite> findByPlayerId(Long playerId);
  List<AllianceInvite> findByAllianceId(Long allianceId);
  Optional<AllianceInvite> findByAllianceIdAndPlayerId(Long allianceId, Long playerId);
  boolean existsByAllianceIdAndPlayerId(Long allianceId, Long playerId);
  @Transactional void deleteByPlayerId(Long playerId);
}
