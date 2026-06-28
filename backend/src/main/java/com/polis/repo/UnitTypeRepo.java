package com.polis.repo;

import com.polis.domain.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UnitTypeRepo extends JpaRepository<UnitType, Long> {
  Optional<UnitType> findByName(String name);
}
