package com.polis.repo;

import com.polis.domain.AllianceForumPost;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AllianceForumPostRepo extends JpaRepository<AllianceForumPost, Long> {
  List<AllianceForumPost> findTop50ByAllianceIdOrderByCreatedAtDesc(Long allianceId);
}
