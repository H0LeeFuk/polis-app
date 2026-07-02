package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="players")
@Getter @Setter @NoArgsConstructor
public class Player {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(unique=true, nullable=false) private String username;
  private String email;
  @Column(name="password_hash", nullable=false) private String passwordHash;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Column(name="alliance_id") private Long allianceId;
  private int level = 1;                                                  // = max cities allowed (cap 20)
  @Column(name="combat_points") private int combatPoints = 0;            // spendable war currency (festival fuel) — top-right HUD
  @Column(name="combat_points_total") private int combatPointsTotal = 0; // lifetime war score (rankings) — never spent
  @Column(name="culture_points") private int culturePoints = 0;          // toward the next level
  @Column(name="culture_points_total") private int culturePointsTotal = 0; // lifetime (prestige/rankings)
  private int gold = 0;     // premium currency: rush construction/training — earned in-game, starts empty
  @Column(name="is_npc") private boolean npc = false;
  private Instant createdAt = Instant.now();
}
