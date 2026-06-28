package com.polis.repo;

import com.polis.domain.PlayerMission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlayerMissionRepo extends JpaRepository<PlayerMission, Long> {
  List<PlayerMission> findByPlayerId(Long playerId);
  Optional<PlayerMission> findByPlayerIdAndMissionId(Long playerId, Long missionId);
  long countByPlayerId(Long playerId);
}
