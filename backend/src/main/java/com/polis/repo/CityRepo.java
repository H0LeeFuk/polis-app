package com.polis.repo;

import com.polis.domain.City;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CityRepo extends JpaRepository<City, Long> {
  List<City> findByPlayerId(Long playerId);
  List<City> findByWorldId(Long worldId);
  List<City> findByIslandId(Long islandId);
  Optional<City> findByPlayerIdAndCapitalTrue(Long playerId);
  Optional<City> findByIslandIdAndSlot(Long islandId, int slot);
  long countByPlayerId(Long playerId);
  long countByWorldIdAndPlayerIdNotNull(Long worldId);

  // Serializes job finalization: rush (finishWithGold), per-read sync(), and the
  // background tick all run finalizeJobs and would otherwise concurrently delete/promote
  // the same queue, producing duplicate/phantom jobs (the "extra build slot" bug).
  // A write lock blocks instead of failing — no 500s, unlike the removed @Version.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from City c where c.id = :id")
  Optional<City> findByIdForUpdate(@Param("id") Long id);

  // Direct name update — avoids loading/saving the whole row, so it can't lose an
  // optimistic-lock race with the background tick or a concurrent resource sync.
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update City c set c.name = :name where c.id = :id")
  void renameById(@Param("id") Long id, @Param("name") String name);
}
