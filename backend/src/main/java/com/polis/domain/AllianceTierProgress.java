package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Progress an alliance has earned IN a given tier — it counts toward unlocking the NEXT tier for
 * founding/conquest. Tier-1 progress gates Tier 2; Tier-2 progress gates Tier 3.
 * {@code bossKills} = island bosses defeated on this tier's islands (any member participated).
 * {@code controlSeconds} = accumulated (parallel-stacking) resource-building control time on this tier.
 */
@Entity @Table(name="alliance_tier_progress",
    uniqueConstraints=@UniqueConstraint(columnNames={"alliance_id","tier"}))
@Getter @Setter @NoArgsConstructor
public class AllianceTierProgress {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="alliance_id", nullable=false) private Long allianceId;
  @Column(nullable=false) private int tier;                 // progress earned in this tier (1|2|3)
  @Column(name="boss_kills", nullable=false) private int bossKills = 0;
  @Column(name="control_seconds", nullable=false) private long controlSeconds = 0;

  public AllianceTierProgress(Long allianceId, int tier){ this.allianceId = allianceId; this.tier = tier; }
}
