package com.polis.repo;

import com.polis.domain.CityBuilding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BuildingRepo extends JpaRepository<CityBuilding, Long> {
  List<CityBuilding> findByCityId(Long cityId);
}
