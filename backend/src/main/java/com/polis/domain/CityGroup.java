package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A player-defined, purely-organizational grouping of their own cities (UI/navigation only —
 * no gameplay effect). A city may belong to many groups (see {@link CityGroupMembership}).
 * Groups are distinguished by a preset {@code icon}; there are no per-city map colors.
 */
@Entity @Table(name="city_groups")
@Getter @Setter @NoArgsConstructor
public class CityGroup {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="owner_player_id", nullable=false) private Long ownerPlayerId;
  @Column(nullable=false) private String name;
  /** Icon id chosen from a preset set (see {@code CityGroupService.ICON_CHOICES}). */
  @Column(nullable=false) private String icon;
  @Column(name="sort_order", nullable=false) private int sortOrder = 0;
}
