package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A daily roaming PvE world boss (a sea monster). Spawns at 21:00, sails an arc of the Tier 2 / Tier 3
 * boundary ring, and despawns at 22:00. Carries a large shared health pool that never regenerates and
 * a daily-varying elemental defence profile (visible to players). Damage is accumulated per alliance
 * (see {@link ColossusDamage}); on defeat the reward pool is split by each alliance's damage share.
 *
 * <p>Position is not stored — it is interpolated from the route fields (centre + radius + start angle
 * + arc span) and the elapsed fraction of the 21:00–22:00 window, like trade/troop movements.
 *
 * <p>(Spec calls for a UUID id; this codebase keys every entity on a Long identity, so we follow suit.)
 */
@Entity @Table(name="colossi")
@Getter @Setter @NoArgsConstructor
public class Colossus {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Column(nullable=false) private String name;
  @Column(nullable=false) private int tier = 1;                 // scales HP + reward pool

  @Column(name="max_health", nullable=false)     private long maxHealth;
  @Column(name="current_health", nullable=false) private long currentHealth;

  @Enumerated(EnumType.STRING) @Column(nullable=false) private ColossusStatus status = ColossusStatus.ROAMING;

  // --- route along the Tier 2/3 ring (an arc swept over the hour) ---
  @Column(name="center_x", nullable=false) private int centerX;
  @Column(name="center_y", nullable=false) private int centerY;
  @Column(nullable=false) private int radius;
  @Column(name="start_angle", nullable=false) private double startAngle;  // radians
  @Column(name="arc_span", nullable=false)    private double arcSpan;     // radians swept by despawn

  // --- daily elemental combat profile (re-rolled each spawn; total bulk kept ~constant) ---
  @Enumerated(EnumType.STRING) @Column(name="attack_element", nullable=false) private Element attackElement = Element.WATER;
  @Column(name="defense_fire",  nullable=false) private int defenseFire;
  @Column(name="defense_wind",  nullable=false) private int defenseWind;
  @Column(name="defense_earth", nullable=false) private int defenseEarth;
  @Column(name="defense_water", nullable=false) private int defenseWater;

  @Column(name="spawned_at", nullable=false) private Instant spawnedAt;
  @Column(name="despawn_at", nullable=false) private Instant despawnAt;

  public int defenseOf(Element e){
    return switch (e){ case FIRE -> defenseFire; case WIND -> defenseWind; case EARTH -> defenseEarth; case WATER -> defenseWater; };
  }
}
