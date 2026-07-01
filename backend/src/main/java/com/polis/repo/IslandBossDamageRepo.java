package com.polis.repo;

import com.polis.domain.IslandBossDamage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IslandBossDamageRepo extends JpaRepository<IslandBossDamage, Long> {
  List<IslandBossDamage> findByBossIdOrderByAccumulatedDamageDesc(Long bossId);
  Optional<IslandBossDamage> findByBossIdAndPlayerId(Long bossId, Long playerId);
  @Modifying @Query("delete from IslandBossDamage d where d.bossId = :bossId")
  void deleteByBossId(@Param("bossId") Long bossId);
}
