package com.polis.repo;

import com.polis.domain.Alliance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AllianceRepo extends JpaRepository<Alliance, Long> {
  List<Alliance> findByWorldId(Long worldId);
}
