package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;

/** A stack of one unit type stationed in a city. {@code type} is the {@link UnitType#getName()}. */
@Entity @Table(name="city_units")
@Getter @Setter @NoArgsConstructor
public class CityUnit {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="city_id", nullable=false) private Long cityId;
  @Column(nullable=false) private String type;   // unit name, e.g. "HOPLITE"
  private int count = 0;
  public CityUnit(Long cityId, String type, int count){ this.cityId=cityId; this.type=type; this.count=count; }
}
