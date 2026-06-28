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
}
