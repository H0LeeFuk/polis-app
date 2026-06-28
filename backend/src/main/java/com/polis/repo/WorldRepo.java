package com.polis.repo;

import com.polis.domain.World;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldRepo extends JpaRepository<World, Long> {}
