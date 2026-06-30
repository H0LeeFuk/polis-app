package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="islands")
@Getter @Setter @NoArgsConstructor
public class Island {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id") private Long worldId;
  private String name;
  @Column(name="ocean_x") private int oceanX;
  @Column(name="ocean_y") private int oceanY;
  private int px;
  private int py;
  private long seed;
  @Column(name="is_resource") private boolean resource = false;
  /** Disc tier (INVERTED clustered model): 1 = outer/weak (spawn), 2 = mid, 3 = core/strong. 0 = wonder. */
  private int tier = 0;
  /** Player islands only: true = GREEN (players may spawn here), false = RED (founding/conquest only). */
  private boolean spawnable = false;
  /** Which repeating cluster this island belongs to (centre yellow + 5 surrounding + 2 side share an id). */
  @Column(name="cluster_id") private int clusterId = 0;
  /** Resource islands only: yield strength 1..3 scaling inward (T1<T2<T3); 0 for player islands. */
  @Column(name="resource_level") private int resourceLevel = 0;
}
