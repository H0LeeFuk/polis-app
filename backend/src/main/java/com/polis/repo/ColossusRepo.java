package com.polis.repo;

import com.polis.domain.Colossus;
import com.polis.domain.ColossusStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ColossusRepo extends JpaRepository<Colossus, Long> {
  List<Colossus> findByWorldIdAndStatus(Long worldId, ColossusStatus status);
  List<Colossus> findByStatus(ColossusStatus status);
}
