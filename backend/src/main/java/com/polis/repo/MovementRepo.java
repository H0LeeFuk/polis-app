package com.polis.repo;

import com.polis.domain.Movement;
import com.polis.domain.MovementPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MovementRepo extends JpaRepository<Movement, Long> {
  List<Movement> findByPlayerIdAndResolvedFalse(Long playerId);
  List<Movement> findBySourceCityIdAndResolvedFalse(Long sourceCityId);
  List<Movement> findByTargetCityIdAndResolvedFalse(Long targetCityId);
  List<Movement> findByTargetCityIdInAndResolvedFalse(Collection<Long> targetCityIds);
  List<Movement> findByPlayerIdAndPhaseAndResolvedFalse(Long playerId, MovementPhase phase);
  List<Movement> findByPhaseAndResolvedFalse(MovementPhase phase);
  // A SETTLE movement becomes "due" once, on arrival; thereafter it waits unresolved with
  // arrivedAt set. Excluding arrivedAt!=null keeps it from re-firing every tick.
  @Query("select m from Movement m where m.resolved=false and m.arriveAt<=:now and m.arrivedAt is null")
  List<Movement> findDue(@Param("now") Instant now);
}
