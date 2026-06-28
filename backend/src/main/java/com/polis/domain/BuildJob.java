package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="build_jobs")
@Getter @Setter @NoArgsConstructor
public class BuildJob {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="city_id", nullable=false) private Long cityId;
  @Enumerated(EnumType.STRING) @Column(name="queue_type") private QueueType queueType;
  @Enumerated(EnumType.STRING) @Column(name="building_type") private BuildingType buildingType;
  @Column(name="to_level") private Integer toLevel;
  @Enumerated(EnumType.STRING) @Column(name="unit_type") private UnitType unitType;
  private Integer batch;
  private int position;
  @Column(name="started_at") private Instant startedAt;
  @Column(name="finish_at") private Instant finishAt;
  @Column(name="total_seconds") private int totalSeconds;
  private Instant createdAt = Instant.now();
}
