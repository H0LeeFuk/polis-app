package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="city_research")
@Getter @Setter @NoArgsConstructor
public class CityResearch {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="city_id", nullable=false) private Long cityId;
  @Enumerated(EnumType.STRING) private ResearchType type;
  public CityResearch(Long cityId, ResearchType type){ this.cityId=cityId; this.type=type; }
}
