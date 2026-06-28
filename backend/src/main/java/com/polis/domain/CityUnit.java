package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="city_units")
@Getter @Setter @NoArgsConstructor
public class CityUnit {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="city_id", nullable=false) private Long cityId;
  @Enumerated(EnumType.STRING) private UnitType type;
  private int count = 0;
  public CityUnit(Long cityId, UnitType type, int count){ this.cityId=cityId; this.type=type; this.count=count; }
}
