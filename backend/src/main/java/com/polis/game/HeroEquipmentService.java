package com.polis.game;

import com.polis.domain.Hero;
import com.polis.domain.HeroItem;
import com.polis.repo.HeroItemRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Aggregates equipped {@link HeroItem} buffs into the hero's combat math, and serves the
 * inventory / equip / unequip actions. Buff values are stored as percentages; the {@code *Pct}
 * methods return fractions (e.g. +6% -> 0.06) to layer on top of attribute bonuses.
 */
@Service
public class HeroEquipmentService {
  private final HeroItemRepo items;
  public HeroEquipmentService(HeroItemRepo items){ this.items = items; }

  private List<HeroItem> equipped(Hero h){
    List<Long> ids = new ArrayList<>();
    if (h.getEquippedWeaponId()!=null) ids.add(h.getEquippedWeaponId());
    if (h.getEquippedArmorId()!=null)  ids.add(h.getEquippedArmorId());
    if (h.getEquippedAmuletId()!=null) ids.add(h.getEquippedAmuletId());
    if (ids.isEmpty()) return List.of();
    return items.findAllById(ids);
  }

  private double sum(Hero h, String buff){
    double s = 0;
    for (HeroItem it : equipped(h)) s += it.getBuffs().getOrDefault(buff, 0);
    return s / 100.0;
  }

  public double attackPct(Hero h){ return sum(h, "ATTACK_PCT"); }
  public double defensePct(Hero h){ return (sum(h,"DEFENSE_BLUNT_PCT")+sum(h,"DEFENSE_DISTANCE_PCT"))/2.0; }
  public double defenseSharpPct(Hero h){ return sum(h, "DEFENSE_SHARP_PCT"); }
  public double defenseBluntPct(Hero h){ return sum(h, "DEFENSE_BLUNT_PCT"); }
  public double defenseDistancePct(Hero h){ return sum(h, "DEFENSE_DISTANCE_PCT"); }
  public double travelPct(Hero h){ return sum(h, "TRAVEL_TIME_PCT"); }
  public double lootPct(Hero h){ return sum(h, "LOOT_PCT"); }
  public double dropChancePct(Hero h){ return sum(h, "DROP_CHANCE_PCT"); }

  public Map<String,Object> equippedDto(Hero h){
    Map<Long,HeroItem> byId = new HashMap<>();
    for (HeroItem it : equipped(h)) byId.put(it.getId(), it);
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("weapon", h.getEquippedWeaponId()==null ? null : itemDto(byId.get(h.getEquippedWeaponId())));
    m.put("armor",  h.getEquippedArmorId()==null  ? null : itemDto(byId.get(h.getEquippedArmorId())));
    m.put("amulet", h.getEquippedAmuletId()==null  ? null : itemDto(byId.get(h.getEquippedAmuletId())));
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
    m.put("equipped", it.isEquipped());
    m.put("seen", it.isSeen());
    return m;
  }

  // --- inventory actions (used by HeroController) -----------------------------

  @Transactional(readOnly = true)
  public List<Map<String,Object>> inventory(Long playerId){
    return items.findByOwnerPlayerId(playerId).stream()
        .sorted(Comparator.comparing(HeroItem::getObtainedAt).reversed())
        .map(this::itemDto).toList();
  }

  @Transactional
  public void markInventorySeen(Long playerId){
    for (HeroItem it : items.findByOwnerPlayerId(playerId)) if (!it.isSeen()){ it.setSeen(true); items.save(it); }
  }

  @Transactional
  public void equip(Hero h, Long itemId){
    HeroItem it = items.findById(itemId).orElseThrow(() -> new IllegalArgumentException("Item not found"));
    if (!Objects.equals(it.getOwnerPlayerId(), h.getOwnerPlayerId())) throw new IllegalStateException("Not your item");
    // unequip whatever is in that slot
    Long current = switch (it.getSlot()){
      case WEAPON -> h.getEquippedWeaponId(); case ARMOR -> h.getEquippedArmorId(); case AMULET -> h.getEquippedAmuletId();
    };
    if (current != null) items.findById(current).ifPresent(old -> { old.setEquipped(false); items.save(old); });
    switch (it.getSlot()){
      case WEAPON -> h.setEquippedWeaponId(it.getId());
      case ARMOR  -> h.setEquippedArmorId(it.getId());
      case AMULET -> h.setEquippedAmuletId(it.getId());
    }
    it.setEquipped(true); it.setSeen(true); items.save(it);
  }

  @Transactional
  public void unequip(Hero h, HeroItem.Slot slot){
    Long current = switch (slot){
      case WEAPON -> h.getEquippedWeaponId(); case ARMOR -> h.getEquippedArmorId(); case AMULET -> h.getEquippedAmuletId();
    };
    if (current != null) items.findById(current).ifPresent(it -> { it.setEquipped(false); items.save(it); });
    switch (slot){
      case WEAPON -> h.setEquippedWeaponId(null);
      case ARMOR  -> h.setEquippedArmorId(null);
      case AMULET -> h.setEquippedAmuletId(null);
    }
  }
}
