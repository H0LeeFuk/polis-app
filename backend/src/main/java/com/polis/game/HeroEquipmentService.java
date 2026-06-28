package com.polis.game;

import com.polis.domain.AttackType;
import com.polis.domain.Hero;
import com.polis.domain.HeroItem;
import com.polis.repo.HeroItemRepo;
import com.polis.repo.HeroRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Aggregates equipped {@link HeroItem} buffs into a hero's combat math and resolves the four
 * equipment slots (Weapon/Armor/Relic/Pet). Standard {@code buffs} are percentages summed across
 * equipped items; {@code *Pct} methods return fractions (e.g. +6% -> 0.06). Discrete
 * {@code specialEffects} are surfaced through typed accessors with stacking caps so they stay safe.
 *
 * <p>Only the EQUIPPED hero's items count, and only the hero taking part in an action. An item
 * exists once and can be equipped on only one hero at a time.
 */
@Service
public class HeroEquipmentService {
  /** Never reduce a defender below 25% effective defence — armour-pen ceiling. */
  public static final double ARMOR_PEN_CAP = 0.25;

  private final HeroItemRepo items;
  private final HeroRepo heroes;
  public HeroEquipmentService(HeroItemRepo items, HeroRepo heroes){ this.items = items; this.heroes = heroes; }

  // --- equipped lookup --------------------------------------------------------

  private List<HeroItem> equipped(Hero h){
    List<Long> ids = new ArrayList<>();
    if (h.getEquippedWeaponId()!=null) ids.add(h.getEquippedWeaponId());
    if (h.getEquippedArmorId()!=null)  ids.add(h.getEquippedArmorId());
    if (h.getEquippedRelicId()!=null)  ids.add(h.getEquippedRelicId());
    if (h.getEquippedPetId()!=null)    ids.add(h.getEquippedPetId());
    if (ids.isEmpty()) return List.of();
    return items.findAllById(ids);
  }

  private Long slotId(Hero h, HeroItem.Slot slot){
    return switch (slot){
      case WEAPON -> h.getEquippedWeaponId(); case ARMOR -> h.getEquippedArmorId();
      case RELIC  -> h.getEquippedRelicId();  case PET   -> h.getEquippedPetId();
    };
  }
  private void setSlot(Hero h, HeroItem.Slot slot, Long id){
    switch (slot){
      case WEAPON -> h.setEquippedWeaponId(id); case ARMOR -> h.setEquippedArmorId(id);
      case RELIC  -> h.setEquippedRelicId(id);  case PET   -> h.setEquippedPetId(id);
    }
  }

  // --- standard buff sums (fractions) ----------------------------------------

  private double sum(Hero h, String buff){
    double s = 0;
    for (HeroItem it : equipped(h)) s += it.getBuffs().getOrDefault(buff, 0);
    return s / 100.0;
  }

  public double attackPct(Hero h){ return sum(h, "ATTACK_PCT"); }
  public double defensePct(Hero h){ return sum(h, "DEFENSE_PCT"); }                 // all-type defence
  public double defenseSharpPct(Hero h){ return sum(h, "DEFENSE_SHARP_PCT"); }      // extra vs sharp
  public double defenseBluntPct(Hero h){ return sum(h, "DEFENSE_BLUNT_PCT"); }
  public double defenseDistancePct(Hero h){ return sum(h, "DEFENSE_DISTANCE_PCT"); }
  public double travelPct(Hero h){ return sum(h, "TRAVEL_TIME_PCT"); }
  public double navalTravelPct(Hero h){ return sum(h, "NAVAL_TRAVEL_TIME_PCT"); }
  public double lootPct(Hero h){ return sum(h, "LOOT_PCT"); }
  public double dropChancePct(Hero h){ return sum(h, "DROP_CHANCE_PCT"); }
  public double heroXpPct(Hero h){ return sum(h, "HERO_XP_PCT"); }
  public double skillCooldownPct(Hero h){ return sum(h, "SKILL_COOLDOWN_PCT"); }
  public double woundRecoveryPct(Hero h){ return sum(h, "WOUND_RECOVERY_PCT"); }
  public double lossReductionPct(Hero h){ return sum(h, "LOSS_REDUCTION_PCT"); }

  // --- special effects (capped, named) ---------------------------------------

  private List<Map<String,Object>> effectsOf(Hero h, String type){
    List<Map<String,Object>> out = new ArrayList<>();
    for (HeroItem it : equipped(h))
      for (Map<String,Object> e : it.getSpecialEffects())
        if (type.equals(e.get("effectType"))) out.add(e);
    return out;
  }
  @SuppressWarnings("unchecked")
  private static Map<String,Object> params(Map<String,Object> effect){
    Object p = effect.get("params");
    return p instanceof Map ? (Map<String,Object>) p : Map.of();
  }
  private static double num(Map<String,Object> params, String key){
    Object v = params.get(key);
    return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
  }

  /** Fraction of the defender's defence (of {@code against}) this hero's army ignores, capped. */
  public double armorPen(Hero h, AttackType against){
    double s = 0;
    for (Map<String,Object> e : effectsOf(h, "ARMOR_PEN_PCT")){
      Map<String,Object> p = params(e);
      String t = String.valueOf(p.getOrDefault("type", "ALL"));
      if ("ALL".equalsIgnoreCase(t) || t.equalsIgnoreCase(against.name())) s += num(p, "value");
    }
    return Math.min(ARMOR_PEN_CAP, s);
  }
  /** Pre-combat bonus volley as a fraction of attack — once per battle (strongest item wins). */
  public double firstStrikePct(Hero h){
    double max = 0; for (Map<String,Object> e : effectsOf(h, "FIRST_STRIKE")) max = Math.max(max, num(params(e),"value"));
    return max;
  }
  /** True if any item grants a loss-free opening round (once per battle, not summed). */
  public boolean extraSafeRound(Hero h){ return !effectsOf(h, "EXTRA_SAFE_ROUND").isEmpty(); }
  /** Fraction of lost defenders recovered after a successful defence (capped 50%). */
  public double retaliationHealPct(Hero h){
    double s = 0; for (Map<String,Object> e : effectsOf(h, "RETALIATION_HEAL")) s += num(params(e),"value");
    return Math.min(0.50, s);
  }
  /** Chance a victory yields a bonus haul (capped 50%). */
  public double luckyHaulChance(Hero h){
    double s = 0; for (Map<String,Object> e : effectsOf(h, "LUCKY_HAUL")) s += num(params(e),"value");
    return Math.min(0.50, s);
  }
  /** Extra siege/wall damage fraction. */
  public double wallDamageBonusPct(Hero h){
    double s = 0; for (Map<String,Object> e : effectsOf(h, "WALL_DAMAGE_BONUS_PCT")) s += num(params(e),"value");
    return s;
  }
  /** Extra defence fraction that only applies when the hero defends in a city. */
  public double cityDefenseBonusPct(Hero h){
    double s = 0; for (Map<String,Object> e : effectsOf(h, "CITY_DEFENSE_BONUS_PCT")) s += num(params(e),"value");
    return s;
  }
  public boolean scoutReveal(Hero h){ return !effectsOf(h, "SCOUT_REVEAL").isEmpty(); }
  public boolean landPenaltyImmunity(Hero h){ return !effectsOf(h, "LAND_PENALTY_IMMUNITY").isEmpty(); }

  /** Human-readable names of the special effects that are live on this hero (for reports/UI). */
  public List<String> activeEffectLabels(Hero h){
    List<String> out = new ArrayList<>();
    for (HeroItem it : equipped(h))
      for (Map<String,Object> e : it.getSpecialEffects())
        out.add(it.getName() + ": " + effectLabel(String.valueOf(e.get("effectType")), params(e)));
    return out;
  }

  static String effectLabel(String type, Map<String,Object> p){
    return switch (type){
      case "ARMOR_PEN_PCT" -> "Ignores " + pct(num(p,"value")) + "% of "
          + String.valueOf(p.getOrDefault("type","all")).toLowerCase() + " defence";
      case "EXTRA_SAFE_ROUND" -> "No losses for the first round";
      case "WALL_DAMAGE_BONUS_PCT" -> "+" + pct(num(p,"value")) + "% wall damage";
      case "SCOUT_REVEAL" -> "Reveals enemy army composition";
      case "LAND_PENALTY_IMMUNITY" -> "Swimmers ignore the land-speed penalty";
      case "FIRST_STRIKE" -> "Bonus pre-combat strike (+" + pct(num(p,"value")) + "%)";
      case "RETALIATION_HEAL" -> "Recovers " + pct(num(p,"value")) + "% of lost defenders on a win";
      case "LUCKY_HAUL" -> pct(num(p,"value")) + "% chance of a bonus haul";
      case "CITY_DEFENSE_BONUS_PCT" -> "+" + pct(num(p,"value")) + "% defence when defending a city";
      default -> type;
    };
  }
  private static int pct(double v){ return (int)Math.round(v * 100); }

  // --- DTOs -------------------------------------------------------------------

  public Map<String,Object> equippedDto(Hero h){
    Map<Long,HeroItem> byId = new HashMap<>();
    for (HeroItem it : equipped(h)) byId.put(it.getId(), it);
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("weapon", itemDto(byId.get(h.getEquippedWeaponId())));
    m.put("armor",  itemDto(byId.get(h.getEquippedArmorId())));
    m.put("relic",  itemDto(byId.get(h.getEquippedRelicId())));
    m.put("pet",    itemDto(byId.get(h.getEquippedPetId())));
    return m;
  }

  public Map<String,Object> itemDto(HeroItem it){
    if (it == null) return null;
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", it.getId());
    m.put("name", it.getName());
    m.put("slot", it.getSlot().name());
    m.put("rarity", it.getRarity().name());
    m.put("buffs", it.getBuffs());
    m.put("specialEffects", it.getSpecialEffects());
    List<String> fx = new ArrayList<>();
    for (Map<String,Object> e : it.getSpecialEffects())
      fx.add(effectLabel(String.valueOf(e.get("effectType")), params(e)));
    m.put("effectLabels", fx);
    m.put("equipped", it.isEquipped());
    m.put("seen", it.isSeen());
    return m;
  }

  // --- inventory actions ------------------------------------------------------

  @Transactional(readOnly = true)
  public List<Map<String,Object>> inventory(Long playerId){
    // map equipped item id -> hero name so the UI can show "Equipped on Leo"
    Map<Long,String> equippedOn = new HashMap<>();
    for (Hero h : heroes.findByOwnerPlayerId(playerId)){
      if (h.getEquippedWeaponId()!=null) equippedOn.put(h.getEquippedWeaponId(), h.getName());
      if (h.getEquippedArmorId()!=null)  equippedOn.put(h.getEquippedArmorId(),  h.getName());
      if (h.getEquippedRelicId()!=null)  equippedOn.put(h.getEquippedRelicId(),  h.getName());
      if (h.getEquippedPetId()!=null)    equippedOn.put(h.getEquippedPetId(),    h.getName());
    }
    return items.findByOwnerPlayerId(playerId).stream()
        .sorted(Comparator.comparing(HeroItem::getObtainedAt).reversed())
        .map(it -> { Map<String,Object> m = itemDto(it); m.put("equippedOn", equippedOn.get(it.getId())); return m; })
        .toList();
  }

  @Transactional
  public void markInventorySeen(Long playerId){
    for (HeroItem it : items.findByOwnerPlayerId(playerId)) if (!it.isSeen()){ it.setSeen(true); items.save(it); }
  }

  @Transactional
  public void equip(Hero h, Long itemId){
    HeroItem it = items.findById(itemId).orElseThrow(() -> new IllegalArgumentException("Item not found"));
    if (!Objects.equals(it.getOwnerPlayerId(), h.getOwnerPlayerId())) throw new IllegalStateException("Not your item");
    // an item exists once — if it's on the OTHER hero, the player must unequip it there first
    if (it.isEquipped()){
      for (Hero other : heroes.findByOwnerPlayerId(h.getOwnerPlayerId())){
        if (Objects.equals(other.getId(), h.getId())) continue;
        if (Objects.equals(slotId(other, it.getSlot()), itemId))
          throw new IllegalStateException("That item is equipped on " + other.getName() + " — unequip it there first");
      }
    }
    // free whatever currently sits in that slot on this hero
    Long current = slotId(h, it.getSlot());
    if (current != null && !Objects.equals(current, itemId))
      items.findById(current).ifPresent(old -> { old.setEquipped(false); items.save(old); });
    setSlot(h, it.getSlot(), it.getId());
    it.setEquipped(true); it.setSeen(true); items.save(it);
  }

  @Transactional
  public void unequip(Hero h, HeroItem.Slot slot){
    Long current = slotId(h, slot);
    if (current != null) items.findById(current).ifPresent(it -> { it.setEquipped(false); items.save(it); });
    setSlot(h, slot, null);
  }
}
