package com.polis.repo;

import com.polis.domain.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlayerRepo extends JpaRepository<Player, Long> {
  Optional<Player> findByUsername(String username);
  boolean existsByUsername(String username);
  boolean existsByEmail(String email);
  List<Player> findByWorldId(Long worldId);
}
