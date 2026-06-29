package com.polis.repo;

import com.polis.domain.SpyMission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SpyMissionRepo extends JpaRepository<SpyMission, Long> {
  List<SpyMission> findByStatusAndResolvesAtLessThanEqual(SpyMission.Status status, Instant now);
  List<SpyMission> findBySpyingPlayerIdAndStatus(Long spyingPlayerId, SpyMission.Status status);
}
