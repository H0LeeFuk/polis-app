package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;

/** Many-to-many link between a {@link CityGroup} and a city. A city can have many memberships. */
@Entity @Table(name="city_group_memberships",
    uniqueConstraints=@UniqueConstraint(columnNames={"city_group_id","city_id"}))
@Getter @Setter @NoArgsConstructor
public class CityGroupMembership {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="city_group_id", nullable=false) private Long cityGroupId;
  @Column(name="city_id", nullable=false) private Long cityId;

  public CityGroupMembership(Long cityGroupId, Long cityId){ this.cityGroupId = cityGroupId; this.cityId = cityId; }
}
