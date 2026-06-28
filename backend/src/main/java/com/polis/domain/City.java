package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="cities")
@Getter @Setter @NoArgsConstructor
public class City {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Column(name="player_id") private Long playerId;          // null => barbarian
  @Column(name="island_id", nullable=false) private Long islandId;
  private int slot;
  private String name;
  @Column(name="is_capital") private boolean capital = false;
  @Enumerated(EnumType.STRING) private GodType god;
  private double wood, stone, silver, favor, power;
  private int points;
  @Column(name="last_tick_at") private Instant lastTickAt = Instant.now();
  private Instant createdAt = Instant.now();
  @Version private Long version;  // optimistic locking guards against double-spend races
}
