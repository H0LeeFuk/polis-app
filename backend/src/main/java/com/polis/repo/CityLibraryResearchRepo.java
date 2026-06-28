package com.polis.repo;

import com.polis.domain.CityLibraryResearch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface CityLibraryResearchRepo extends JpaRepository<CityLibraryResearch, Long> {
  List<CityLibraryResearch> findByCityId(Long cityId);
  List<CityLibraryResearch> findByStatusAndCompletesAtLessThanEqual(CityLibraryResearch.Status status, Instant when);
  void deleteByCityId(Long cityId);
}
