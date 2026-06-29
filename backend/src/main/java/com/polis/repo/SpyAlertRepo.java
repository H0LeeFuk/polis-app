package com.polis.repo;

import com.polis.domain.SpyAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpyAlertRepo extends JpaRepository<SpyAlert, Long> {
  List<SpyAlert> findByOwnerPlayerIdOrderByCaughtAtDesc(Long ownerPlayerId);
}
