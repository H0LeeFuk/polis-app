package com.polis.repo;

import com.polis.domain.IslandBoss;
import com.polis.domain.IslandBossStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface IslandBossRepo extends JpaRepository<IslandBoss, Long> {
  Optional<IslandBoss> findByIslandId(Long islandId);
  List<IslandBoss> findByWorldId(Long worldId);
  List<IslandBoss> findByStatus(IslandBossStatus status);
}
