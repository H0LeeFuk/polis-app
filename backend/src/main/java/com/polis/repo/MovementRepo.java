package com.polis.repo;

import com.polis.domain.Movement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface MovementRepo extends JpaRepository<Movement, Long> {
  List<Movement> findByPlayerIdAndResolvedFalse(Long playerId);
  @Query("select m from Movement m where m.resolved=false and m.arriveAt<=:now")
  List<Movement> findDue(@Param("now") Instant now);
}
