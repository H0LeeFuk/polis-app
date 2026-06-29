package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** A single message on an alliance's forum. */
@Entity @Table(name="alliance_forum_posts")
@Getter @Setter @NoArgsConstructor
public class AllianceForumPost {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="alliance_id", nullable=false) private Long allianceId;
  @Column(name="author_player_id", nullable=false) private Long authorPlayerId;
  @Column(nullable=false) private String body;
  @Column(name="created_at") private Instant createdAt = Instant.now();
}
