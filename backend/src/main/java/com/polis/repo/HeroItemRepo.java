package com.polis.repo;

import com.polis.domain.HeroItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HeroItemRepo extends JpaRepository<HeroItem, Long> {
  List<HeroItem> findByOwnerPlayerId(Long ownerPlayerId);
}
