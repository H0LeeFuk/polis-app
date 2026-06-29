package com.polis.repo;

import com.polis.domain.ColossusDamage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ColossusDamageRepo extends JpaRepository<ColossusDamage, Long> {
  List<ColossusDamage> findByColossusIdOrderByAccumulatedDamageDesc(Long colossusId);
  Optional<ColossusDamage> findByColossusIdAndAllianceId(Long colossusId, Long allianceId);
}
