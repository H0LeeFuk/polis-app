package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * Seeded config for one bandit-camp level: the defenders to beat and the reward. The reward
 * payload mixes resource keys (wood/stone/silver) and unit names freely; {@code rewardType}
 * is a display hint.
 */
@Entity @Table(name="bandit_camp_levels")
@Getter @Setter @NoArgsConstructor
public class BanditCampLevel {
  public enum RewardType { RESOURCES, TROOPS, MIXED }

  @Id private int level;   // 1..10
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="defender_troops", columnDefinition="json")
  private Map<String,Integer> defenderTroops = new HashMap<>();
  @Enumerated(EnumType.STRING) @Column(name="reward_type") private RewardType rewardType = RewardType.RESOURCES;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="reward_payload", columnDefinition="json")
  private Map<String,Integer> rewardPayload = new HashMap<>();
  private String description;
}
