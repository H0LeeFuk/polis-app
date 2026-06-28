package com.polis.repo;

import com.polis.domain.BanditCamp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BanditCampRepo extends JpaRepository<BanditCamp, Long> {
  Optional<BanditCamp> findByIslandId(Long islandId);
}
