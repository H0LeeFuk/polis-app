package com.polis.repo;

import com.polis.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CityRepo extends JpaRepository<City, Long> {
  List<City> findByPlayerId(Long playerId);
  List<City> findByWorldId(Long worldId);
  List<City> findByIslandId(Long islandId);
  Optional<City> findByPlayerIdAndCapitalTrue(Long playerId);
  Optional<City> findByIslandIdAndSlot(Long islandId, int slot);
  long countByPlayerId(Long playerId);
}
