package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="city_buildings")
@Getter @Setter @NoArgsConstructor
public class CityBuilding {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="city_id", nullable=false) private Long cityId;
  @Enumerated(EnumType.STRING) private BuildingType type;
  private int level = 0;
  public CityBuilding(Long cityId, BuildingType type, int level){ this.cityId=cityId; this.type=type; this.level=level; }
}
