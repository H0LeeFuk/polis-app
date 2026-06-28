package com.polis.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One account-level hero per player. Levels via battle XP, accompanies a single army at a
 * time, never dies (worst case {@link HeroState#WOUNDED} then recovers). Attribute points
 * feed Leadership/Cunning/Valor; unlocked skills are armed before an action.
 */
@Entity @Table(name="heroes")
@Getter @Setter @NoArgsConstructor
public class Hero {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  // Two heroes per player now — no longer unique on owner.
  @Column(name="owner_player_id", nullable=false) private Long ownerPlayerId;
  @Column(nullable=false) private String name;

  /** Which fixed hero this is (LEO / TITANIA). */
  @Enumerated(EnumType.STRING) @Column(name="hero_key", nullable=false) private HeroKey heroKey = HeroKey.LEO;
  /** Hero race flavour: LEO=HUMANS, TITANIA=FAIRIES. Drives per-hero bonus scaling. */
  @Enumerated(EnumType.STRING) @Column(nullable=false) private Race race = Race.HUMANS;
  /** False until unlocked (TITANIA starts locked until the starter mission chain is complete). */
  @Column(nullable=false) private boolean unlocked = true;

  private int level = 1;
  @Column(name="current_xp") private long currentXp = 0;
  @Column(name="xp_to_next_level") private long xpToNextLevel = 100;
  @Column(name="unspent_attribute_points") private int unspentAttributePoints = 0;

  @Column(name="attr_leadership") private int attrLeadership = 0;
  @Column(name="attr_cunning")    private int attrCunning = 0;
  @Column(name="attr_valor")      private int attrValor = 0;

  @Enumerated(EnumType.STRING) @Column(nullable=false) private HeroState state = HeroState.IDLE;
  @Column(name="stationed_city_id")  private Long stationedCityId;
  @Column(name="active_movement_id") private Long activeMovementId;
  @Column(name="wounded_until")      private Instant woundedUntil;

  @JdbcTypeCode(SqlTypes.JSON) @Column(name="unlocked_skills", columnDefinition="json")
  private List<String> unlockedSkills = new ArrayList<>();
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="skill_cooldowns", columnDefinition="json")
  private Map<String,String> skillCooldowns = new HashMap<>();   // skill -> availableAt (ISO instant)
  /** Skill armed for the next action (consumed on resolve), or null. */
  @Column(name="armed_skill") private String armedSkill;

  // equipment slots (PART 3) — reference HeroItem ids. Four slots: Weapon/Armor/Relic/Pet.
  @Column(name="equipped_weapon_id") private Long equippedWeaponId;
  @Column(name="equipped_armor_id")  private Long equippedArmorId;
  @Column(name="equipped_relic_id")  private Long equippedRelicId;
  @Column(name="equipped_pet_id")    private Long equippedPetId;

  private Instant createdAt = Instant.now();
}
