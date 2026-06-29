package com.polis.repo;

import com.polis.domain.BanditTowerProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BanditTowerProgressRepo extends JpaRepository<BanditTowerProgress, Long> {
  Optional<BanditTowerProgress> findByPlayerId(Long playerId);
}
