package com.polis.repo;

import com.polis.domain.Island;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IslandRepo extends JpaRepository<Island, Long> {
  List<Island> findByWorldId(Long worldId);
}
