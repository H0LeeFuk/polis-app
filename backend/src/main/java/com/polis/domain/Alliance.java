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
  // shared treasury fed by held resource nodes; officers distribute to members
  @Column(name="treasury_wood")  private long treasuryWood = 0;
  @Column(name="treasury_stone") private long treasuryStone = 0;
  @Column(name="treasury_wheat") private long treasuryWheat = 0;
  private Instant createdAt = Instant.now();
}
