package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * The guardian boss of a resource island. One per resource island, themed to a {@link Race}.
 * Defeating it grants resources and a guaranteed rare relic (the only place rares drop now —
 * the island's resource nodes give resources only). After defeat it respawns on a cooldown.
 */
@Entity @Table(name="island_bosses")
@Getter @Setter @NoArgsConstructor
public class IslandBoss {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Column(name="island_id", nullable=false, unique=true) private Long islandId;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private Race race = Race.HUMANS;
  @Column(nullable=false) private String name;
  @Column(nullable=false) private int level = 1;

  @JdbcTypeCode(SqlTypes.JSON) @Column(name="defender_troops", columnDefinition="json")
  private Map<String,Integer> defenderTroops = new HashMap<>();

  @Column(name="defeated_at") private Instant defeatedAt;
  @Column(name="respawn_at")  private Instant respawnAt;   // non-null => currently defeated
}
