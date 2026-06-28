package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="alliances")
@Getter @Setter @NoArgsConstructor
public class Alliance {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id") private Long worldId;
  private String tag;
  private String name;
  @Column(name="leader_id") private Long leaderId;
  private Instant createdAt = Instant.now();
}
