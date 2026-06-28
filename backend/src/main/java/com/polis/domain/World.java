package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="worlds")
@Getter @Setter @NoArgsConstructor
public class World {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  private String name;
  private int speed = 1;
  private Instant createdAt = Instant.now();
}
