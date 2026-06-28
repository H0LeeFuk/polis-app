package com.polis.repo;

import com.polis.domain.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MissionRepo extends JpaRepository<Mission, Long> {
  List<Mission> findByChainOrderByOrderIndexAsc(String chain);
}
