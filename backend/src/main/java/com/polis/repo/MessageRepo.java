package com.polis.repo;

import com.polis.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepo extends JpaRepository<Message, Long> {
  List<Message> findByToPlayerIdOrderBySentAtDesc(Long toPlayerId);
}
