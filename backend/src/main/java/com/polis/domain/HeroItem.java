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
 * A piece of hero equipment in a player's personal inventory. Four slots (Weapon/Armor/Relic/Pet),
 * four rarities. {@code buffs} maps a buff type (e.g. ATTACK_PCT) to a percentage value;
 * {@code specialEffects} is a list of discrete, named engine effects ({@code {effectType, params}}).
 * Items are never race-locked. An item exists once and can be equipped on only one hero at a time.
 */
@Entity @Table(name="hero_items")
@Getter @Setter @NoArgsConstructor
public class HeroItem {
  public enum Slot { WEAPON, ARMOR, RELIC, PET }
  public enum Rarity { COMMON, RARE, EPIC, LEGENDARY }

  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="owner_player_id", nullable=false) private Long ownerPlayerId;
  @Column(nullable=false) private String name;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private Slot slot;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private Rarity rarity;

  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json")
  private Map<String,Integer> buffs = new HashMap<>();                 // buffType -> percent

  @JdbcTypeCode(SqlTypes.JSON) @Column(name="special_effects", columnDefinition="json")
  private List<Map<String,Object>> specialEffects = new ArrayList<>(); // [{effectType, params:{...}}]

  private boolean equipped = false;
  private boolean seen = false;                          // false => "new" badge in inventory
  @Column(name="obtained_at") private Instant obtainedAt = Instant.now();
}
