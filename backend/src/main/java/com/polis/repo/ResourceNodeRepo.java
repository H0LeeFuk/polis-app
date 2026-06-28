package com.polis.repo;

import com.polis.domain.NodeStatus;
import com.polis.domain.ResourceNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResourceNodeRepo extends JpaRepository<ResourceNode, Long> {
  List<ResourceNode> findByIslandId(Long islandId);
  List<ResourceNode> findByWorldId(Long worldId);
  List<ResourceNode> findByControllingPlayerId(Long controllingPlayerId);
  List<ResourceNode> findByStatus(NodeStatus status);
}
