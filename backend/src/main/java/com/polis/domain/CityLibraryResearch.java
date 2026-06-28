package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** One unlocked (or in-progress) Library research per city. */
@Entity @Table(name="city_library_research",
    uniqueConstraints=@UniqueConstraint(columnNames={"city_id","research_id"}))
@Getter @Setter @NoArgsConstructor
public class CityLibraryResearch {
  public enum Status { RESEARCHING, COMPLETED }

  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="city_id", nullable=false) private Long cityId;
  @Column(name="research_id", nullable=false) private String researchId;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status = Status.RESEARCHING;
  @Column(name="started_at") private Instant startedAt = Instant.now();
  @Column(name="completes_at") private Instant completesAt;

  public CityLibraryResearch(Long cityId, String researchId){ this.cityId=cityId; this.researchId=researchId; }
}
