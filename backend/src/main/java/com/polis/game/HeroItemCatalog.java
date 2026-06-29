package com.polis.game;

import com.polis.domain.HeroItem;
import com.polis.domain.HeroItem.Rarity;
import com.polis.domain.HeroItem.Slot;

import java.util.*;

/**
 * Seeded hero-item catalog — the named items that can drop. Each entry carries standard percentage
 * {@code buffs} and discrete {@code specialEffects} ({@code {effectType, params}}). Items are never
 * race-locked. {@link ItemFactory} copies an entry into an owned {@link HeroItem} on drop.
 *
 * <p>Per-type attack flavour ("distance attack") is folded into ATTACK_PCT — the engine has one
 * attacker multiplier. Lower-is-better buffs (travel / cooldown / wound) store a positive percent
 * that the equipment service subtracts.
 */
public final class HeroItemCatalog {
  private HeroItemCatalog(){}

  /** One catalog item: a template copied onto a player's inventory item when it drops. */
  public record Entry(String name, Slot slot, Rarity rarity,
                      Map<String,Integer> buffs, List<Map<String,Object>> specialEffects){}

  // --- effect builders -------------------------------------------------------
  private static Map<String,Integer> buffs(Object... kv){
    Map<String,Integer> m = new LinkedHashMap<>();
    for (int i=0;i<kv.length;i+=2) m.put((String)kv[i], (Integer)kv[i+1]);
    return m;
  }
  @SafeVarargs
  private static List<Map<String,Object>> fx(Map<String,Object>... effects){ return List.of(effects); }
  private static Map<String,Object> effect(String type, Map<String,Object> params){
    Map<String,Object> m = new LinkedHashMap<>(); m.put("effectType", type); m.put("params", params); return m;
  }
  private static Map<String,Object> p(Object... kv){
    Map<String,Object> m = new LinkedHashMap<>();
    for (int i=0;i<kv.length;i+=2) m.put((String)kv[i], kv[i+1]);
    return m;
  }
  private static List<Map<String,Object>> none(){ return List.of(); }

  // --- the catalog -----------------------------------------------------------
  public static final List<Entry> ALL = List.of(
    // ⚔ Weapon
    new Entry("Worn Blade",          Slot.WEAPON, Rarity.COMMON,    buffs("ATTACK_PCT",3), none()),
    new Entry("War Axe",             Slot.WEAPON, Rarity.RARE,      buffs("ATTACK_PCT",7,"LOOT_PCT",5), none()),
    new Entry("Singing Elven Bow",   Slot.WEAPON, Rarity.RARE,      buffs("ATTACK_PCT",10), none()),
    new Entry("Crusher's Maul",      Slot.WEAPON, Rarity.EPIC,      buffs("ATTACK_PCT",12),
        fx(effect("ARMOR_PEN_PCT", p("type","EARTH","value",0.05)))),
    new Entry("Trident of Tides",    Slot.WEAPON, Rarity.EPIC,      buffs("ATTACK_PCT",12,"NAVAL_TRAVEL_TIME_PCT",15), none()),
    new Entry("Fangs of the War-God",Slot.WEAPON, Rarity.LEGENDARY, buffs("ATTACK_PCT",23,"LOOT_PCT",20),
        fx(effect("FIRST_STRIKE", p("value",0.15)))),

    // 🛡 Armor
    new Entry("Scuffed Cuirass",     Slot.ARMOR,  Rarity.COMMON,    buffs("DEFENSE_PCT",3), none()),
    new Entry("Runed Shield",        Slot.ARMOR,  Rarity.RARE,      buffs("DEFENSE_EARTH_PCT",8), none()),
    new Entry("Bark Mantle",         Slot.ARMOR,  Rarity.RARE,      buffs("DEFENSE_PCT",6,"LOSS_REDUCTION_PCT",5), none()),
    new Entry("Bastion Aegis",       Slot.ARMOR,  Rarity.EPIC,      buffs("DEFENSE_PCT",12),
        fx(effect("CITY_DEFENSE_BONUS_PCT", p("value",0.10)))),
    new Entry("Leviathan Carapace",  Slot.ARMOR,  Rarity.EPIC,      buffs("DEFENSE_PCT",10,"WOUND_RECOVERY_PCT",10), none()),
    new Entry("Cloak of the Undying", Slot.ARMOR, Rarity.LEGENDARY, buffs("DEFENSE_PCT",15,"LOSS_REDUCTION_PCT",12,"WOUND_RECOVERY_PCT",50),
        fx(effect("RETALIATION_HEAL", p("value",0.15)))),

    // 🏺 Relic
    new Entry("Broken Compass",      Slot.RELIC,  Rarity.COMMON,    buffs("TRAVEL_TIME_PCT",4), none()),
    new Entry("Hermes' Feather",     Slot.RELIC,  Rarity.RARE,      buffs("TRAVEL_TIME_PCT",10), none()),
    new Entry("Chalice of Plenty",   Slot.RELIC,  Rarity.RARE,      buffs("LOOT_PCT",12), none()),
    new Entry("Seer's Eye",          Slot.RELIC,  Rarity.EPIC,      buffs("LOOT_PCT",15,"DROP_CHANCE_PCT",1),
        fx(effect("SCOUT_REVEAL", p()))),
    new Entry("Sage's Scroll",       Slot.RELIC,  Rarity.EPIC,      buffs("HERO_XP_PCT",20), none()),
    new Entry("Heart of the Oracle", Slot.RELIC,  Rarity.LEGENDARY, buffs("TRAVEL_TIME_PCT",15,"DROP_CHANCE_PCT",2,"SKILL_COOLDOWN_PCT",20), none()),

    // 🐾 Pet
    new Entry("Messenger Crow",      Slot.PET,    Rarity.COMMON,    buffs("HERO_XP_PCT",5), none()),
    new Entry("War Wolf",            Slot.PET,    Rarity.RARE,      buffs("ATTACK_PCT",6,"TRAVEL_TIME_PCT",5), none()),
    new Entry("Glimmer Fae",         Slot.PET,    Rarity.RARE,      buffs("TRAVEL_TIME_PCT",8),
        fx(effect("SCOUT_REVEAL", p()))),
    new Entry("Stone Golem",         Slot.PET,    Rarity.EPIC,      buffs("DEFENSE_PCT",10),
        fx(effect("EXTRA_SAFE_ROUND", p("rounds",1)))),
    new Entry("Sea Serpent",         Slot.PET,    Rarity.EPIC,      buffs("NAVAL_TRAVEL_TIME_PCT",12),
        fx(effect("LAND_PENALTY_IMMUNITY", p()))),
    new Entry("Young Dragon",        Slot.PET,    Rarity.LEGENDARY, buffs("ATTACK_PCT",10,"DEFENSE_PCT",10),
        fx(effect("WALL_DAMAGE_BONUS_PCT", p("value",0.25))))
  );

  /** Catalog entries of a given rarity (for rarity-gated drops, e.g. boss loot). */
  public static List<Entry> ofRarity(Rarity r){
    List<Entry> out = new ArrayList<>();
    for (Entry e : ALL) if (e.rarity()==r) out.add(e);
    return out;
  }
}
