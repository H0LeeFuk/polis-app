package com.polis.repo;

import com.polis.domain.SpyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpyReportRepo extends JpaRepository<SpyReport, Long> {
  List<SpyReport> findByOwnerPlayerIdOrderByCapturedAtDesc(Long ownerPlayerId);
}
