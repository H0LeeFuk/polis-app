package com.polis.repo;

import com.polis.domain.BuildJob;
import com.polis.domain.QueueType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface JobRepo extends JpaRepository<BuildJob, Long> {
  List<BuildJob> findByCityIdOrderByQueueTypeAscPositionAsc(Long cityId);
  List<BuildJob> findByCityIdAndQueueTypeOrderByPositionAsc(Long cityId, QueueType qt);
  long countByCityIdAndQueueType(Long cityId, QueueType qt);
  @Query("select j from BuildJob j where j.position=0 and j.finishAt<=:now")
  List<BuildJob> findDueActiveJobs(@Param("now") Instant now);
}
