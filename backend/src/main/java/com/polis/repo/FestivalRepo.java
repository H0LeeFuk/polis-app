package com.polis.repo;

import com.polis.domain.Festival;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FestivalRepo extends JpaRepository<Festival, Long> {
  List<Festival> findByCityIdOrderByStartedAtDesc(Long cityId);
  long countByCityIdAndStatus(Long cityId, Festival.Status status);
  // both festival types may run at once — the constraint is one running festival PER TYPE, not per city
  long countByCityIdAndFestivalTypeAndStatus(Long cityId, Festival.Type type, Festival.Status status);
  List<Festival> findByCityIdAndStatusOrderByStartedAtDesc(Long cityId, Festival.Status status);
  List<Festival> findByStatusAndCompletesAtLessThanEqual(Festival.Status status, Instant when);
  Optional<Festival> findFirstByCityIdAndStatusOrderByStartedAtDesc(Long cityId, Festival.Status status);
}
