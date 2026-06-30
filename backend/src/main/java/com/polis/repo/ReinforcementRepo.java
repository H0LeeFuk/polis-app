package com.polis.repo;

import com.polis.domain.Reinforcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReinforcementRepo extends JpaRepository<Reinforcement, Long> {
  List<Reinforcement> findByHostCityId(Long hostCityId);
  List<Reinforcement> findByOwnerPlayerId(Long ownerPlayerId);
  Optional<Reinforcement> findByHostCityIdAndOwnerPlayerId(Long hostCityId, Long ownerPlayerId);
  Optional<Reinforcement> findByHostCityIdAndOwnerPlayerIdAndOriginCityId(Long hostCityId, Long ownerPlayerId, Long originCityId);
}
