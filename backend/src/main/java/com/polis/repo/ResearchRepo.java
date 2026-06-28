package com.polis.repo;

import com.polis.domain.CityResearch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResearchRepo extends JpaRepository<CityResearch, Long> {
  List<CityResearch> findByCityId(Long cityId);
}
