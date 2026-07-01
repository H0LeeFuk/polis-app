package com.polis.repo;

import com.polis.domain.AllianceTierProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AllianceTierProgressRepo extends JpaRepository<AllianceTierProgress, Long> {
  Optional<AllianceTierProgress> findByAllianceIdAndTier(Long allianceId, int tier);
  List<AllianceTierProgress> findByAllianceId(Long allianceId);
}
