package com.polis.repo;

import com.polis.domain.WorldState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WorldStateRepo extends JpaRepository<WorldState, Long> {
  Optional<WorldState> findByWorldId(Long worldId);
}
