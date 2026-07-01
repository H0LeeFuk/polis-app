package com.polis.repo;

import com.polis.domain.CityGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityGroupRepo extends JpaRepository<CityGroup, Long> {
  List<CityGroup> findByOwnerPlayerIdOrderBySortOrderAscIdAsc(Long ownerPlayerId);
}
