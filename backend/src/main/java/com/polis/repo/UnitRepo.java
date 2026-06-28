package com.polis.repo;

import com.polis.domain.CityUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UnitRepo extends JpaRepository<CityUnit, Long> {
  List<CityUnit> findByCityId(Long cityId);
}
