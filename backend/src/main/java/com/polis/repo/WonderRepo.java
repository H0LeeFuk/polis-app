package com.polis.repo;

import com.polis.domain.Wonder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WonderRepo extends JpaRepository<Wonder, Long> {
  List<Wonder> findByWorldId(Long worldId);
  List<Wonder> findByIslandId(Long islandId);
  List<Wonder> findByControllingAllianceId(Long controllingAllianceId);
}
