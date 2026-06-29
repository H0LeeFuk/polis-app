package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Accumulated damage one alliance has dealt to a Colossus — the unit of reward distribution. */
@Entity @Table(name="colossus_damage",
    uniqueConstraints=@UniqueConstraint(columnNames={"colossus_id","alliance_id"}))
@Getter @Setter @NoArgsConstructor
public class ColossusDamage {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="colossus_id", nullable=false) private Long colossusId;
  @Column(name="alliance_id", nullable=false) private Long allianceId;
  @Column(name="accumulated_damage", nullable=false) private long accumulatedDamage = 0;
  @Column(name="last_contribution_at") private Instant lastContributionAt;

  public ColossusDamage(Long colossusId, Long allianceId){ this.colossusId=colossusId; this.allianceId=allianceId; }
}
