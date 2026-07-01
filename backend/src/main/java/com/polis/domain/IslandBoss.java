package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * The guardian boss of a resource island — a Colossus-style shared-HP boss, but PER PLAYER and fixed
 * to its island (it never roams). Any player attacks it with sea/flying forces; each resolved attack
 * subtracts damage from the shared pool and tallies it to that player. On defeat the reward pool is
 * split by each player's share of total damage. It then respawns after a cooldown.
 *
 * <p>Distinct from the {@link Colossus} world boss (which roams and pays alliance treasuries).
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
  /** Tier of the island the boss sits on (1 outer/weak .. 3 core/strong) — scales HP + rewards. */
  @Column(nullable=false) private int tier = 1;

  // ---- Colossus-style shared health pool ----
  @Column(name="max_health", nullable=false) private long maxHealth = 0;
  @Column(name="current_health", nullable=false) private long currentHealth = 0;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private IslandBossStatus status = IslandBossStatus.ACTIVE;

  // ---- daily-style elemental defence profile (which element hits it hardest rotates per spawn) ----
  @Enumerated(EnumType.STRING) @Column(name="attack_element", nullable=false) private Element attackElement = Element.FIRE;
  @Column(name="defense_fire",  nullable=false) private int defenseFire = 100;
  @Column(name="defense_wind",  nullable=false) private int defenseWind = 100;
  @Column(name="defense_earth", nullable=false) private int defenseEarth = 100;
  @Column(name="defense_water", nullable=false) private int defenseWater = 100;

  /** Legacy snapshot (unused by the reworked combat, which builds the counter-garrison from level). */
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="defender_troops", columnDefinition="json")
  private Map<String,Integer> defenderTroops = new HashMap<>();

  @Column(name="defeated_at") private Instant defeatedAt;
  @Column(name="respawn_at")  private Instant respawnAt;   // set while DEFEATED — when it returns

  public int defenseOf(Element e){
    return switch (e){ case FIRE -> defenseFire; case WIND -> defenseWind; case EARTH -> defenseEarth; case WATER -> defenseWater; };
  }
}
