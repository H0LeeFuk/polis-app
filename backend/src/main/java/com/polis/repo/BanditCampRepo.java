package com.polis.repo;

import com.polis.domain.BanditCamp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BanditCampRepo extends JpaRepository<BanditCamp, Long> {
  Optional<BanditCamp> findByIslandIdAndPlayerId(Long islandId, Long playerId);
  List<BanditCamp> findByPlayerId(Long playerId);
}
