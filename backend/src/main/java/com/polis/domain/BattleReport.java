package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable record of a resolved ATTACK movement. A single row stores both sides of
 * the battle; the DTO layer adapts the view based on whether the requester is the
 * attacker or the defender. Names are denormalised so reports survive city renames
 * and deletions. The {@code outcome} is always from the attacker's point of view.
 */
@Entity @Table(name="battle_reports")
@Getter @Setter @NoArgsConstructor
public class BattleReport {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Column(name="movement_id") private Long movementId;
  @Column(name="fought_at", nullable=false) private Instant foughtAt = Instant.now();
  @Enumerated(EnumType.STRING) @Column(nullable=false) private BattleOutcome outcome;

  @Column(name="attacker_player_id") private Long attackerPlayerId;          // null only for NPC raiders (none today)
  @Column(name="attacker_city_id") private Long attackerCityId;
  @Column(name="attacker_city_name") private String attackerCityName;
  @Column(name="attacker_player_name") private String attackerPlayerName;

  @Column(name="defender_player_id") private Long defenderPlayerId;          // null => barbarian / unowned city
  @Column(name="defender_city_id") private Long defenderCityId;
  @Column(name="defender_city_name") private String defenderCityName;
  @Column(name="defender_player_name") private String defenderPlayerName;

  @JdbcTypeCode(SqlTypes.JSON) @Column(name="attacker_troops_sent", columnDefinition="json")
  private Map<String,Integer> attackerTroopsSent = new HashMap<>();
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="attacker_troops_lost", columnDefinition="json")
  private Map<String,Integer> attackerTroopsLost = new HashMap<>();
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="attacker_troops_survived", columnDefinition="json")
  private Map<String,Integer> attackerTroopsSurvived = new HashMap<>();

  @JdbcTypeCode(SqlTypes.JSON) @Column(name="defender_troops_present", columnDefinition="json")
  private Map<String,Integer> defenderTroopsPresent = new HashMap<>();
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="defender_troops_lost", columnDefinition="json")
  private Map<String,Integer> defenderTroopsLost = new HashMap<>();
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="defender_troops_survived", columnDefinition="json")
  private Map<String,Integer> defenderTroopsSurvived = new HashMap<>();

  @JdbcTypeCode(SqlTypes.JSON) @Column(name="resources_stolen", columnDefinition="json")
  private Map<String,Long> resourcesStolen = new HashMap<>();

  @Column(name="attacker_attack_power") private int attackerTotalAttackPower;
  @Column(name="defender_defence_power") private int defenderTotalDefencePower;
  @Column(name="siege_damage") private int siegeDamage;

  // PART 1: combat composition by element (FIRE/WIND/EARTH/WATER) for both sides
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="attack_by_element", columnDefinition="json")
  private Map<String,Integer> attackByElement = new HashMap<>();
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="defense_by_element", columnDefinition="json")
  private Map<String,Integer> defenseByElement = new HashMap<>();

  // hero participation (null hero_name => the hero did not take part)
  @Column(name="hero_name") private String heroName;
  @Column(name="hero_level") private int heroLevel;
  @Column(name="hero_attack_bonus_pct") private int heroAttackBonusPct;
  @Column(name="hero_loss_reduction_pct") private int heroLossReductionPct;
  @Column(name="hero_skill_used") private String heroSkillUsed;
  @Column(name="hero_xp_gained") private int heroXpGained;
  @Column(name="hero_leveled_to") private Integer heroLeveledTo;
  @Column(name="hero_wounded") private boolean heroWounded;

  // per-perspective read/deleted flags: attacker and defender each see the same row independently
  @Column(name="attacker_read", nullable=false) private boolean attackerRead = false;
  @Column(name="defender_read", nullable=false) private boolean defenderRead = false;
  @Column(name="attacker_deleted", nullable=false) private boolean attackerDeleted = false;
  @Column(name="defender_deleted", nullable=false) private boolean defenderDeleted = false;
}
