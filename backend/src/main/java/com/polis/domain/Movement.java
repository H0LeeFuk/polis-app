package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity @Table(name="movements")
@Getter @Setter @NoArgsConstructor
public class Movement {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Column(name="player_id") private Long playerId;
  @Column(name="source_city_id") private Long sourceCityId;
  @Column(name="target_city_id") private Long targetCityId;
  @Column(name="target_island_id") private Long targetIslandId;
  @Column(name="target_slot") private Integer targetSlot;
  @Column(name="target_node_id") private Long targetNodeId;   // resource-node moves (OCCUPY / node attack)
  @Column(name="target_camp_id") private Long targetCampId;   // bandit-camp raids (OUT / RETURN)
  @Column(name="target_wonder_id") private Long targetWonderId; // Wonder captures (OUT assault / OCCUPY claim)
  @Column(name="target_colossus_id") private Long targetColossusId; // Colossus strikes (OUT) / march home (RETURN)
  @Enumerated(EnumType.STRING) private MovementPhase phase;

  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json")
  private Map<String,Integer> units = new HashMap<>();
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json")
  private Map<String,Long> loot;

  @Column(name="depart_at") private Instant departAt = Instant.now();
  @Column(name="arrive_at") private Instant arriveAt;
  /** For SETTLE: the moment the hero reached the slot. While set and unresolved the founding
   *  is pending the race choice. Null for every other phase and for in-transit settles. */
  @Column(name="arrived_at") private Instant arrivedAt;
  private boolean resolved = false;
}
