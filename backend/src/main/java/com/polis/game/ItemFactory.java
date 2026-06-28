package com.polis.game;

import com.polis.domain.HeroItem;
import org.springframework.stereotype.Service;

import java.util.*;

/** Rolls a random hero equipment item. Small v1 pool with clear, rarity-scaled effects. */
@Service
public class ItemFactory {
  private static final int[] VALUE = { 3, 6, 10 };   // COMMON, RARE, EPIC

  private record Template(HeroItem.Slot slot, String buff, String[] names){}
  private static final List<Template> TEMPLATES = List.of(
      new Template(HeroItem.Slot.WEAPON, "ATTACK_PCT",          new String[]{"Spear of the Phalanx","Blade of Ares","Bronze Xiphos"}),
      new Template(HeroItem.Slot.ARMOR,  "DEFENSE_SHARP_PCT",   new String[]{"Hoplon of Sparta","Aegis Shield"}),
      new Template(HeroItem.Slot.ARMOR,  "DEFENSE_BLUNT_PCT",   new String[]{"Bronze Cuirass","Linothorax"}),
      new Template(HeroItem.Slot.ARMOR,  "DEFENSE_DISTANCE_PCT",new String[]{"Tower Shield","Pavise of Crete"}),
      new Template(HeroItem.Slot.AMULET, "LOOT_PCT",            new String[]{"Hermes' Charm","Merchant's Talisman"}),
      new Template(HeroItem.Slot.AMULET, "TRAVEL_TIME_PCT",     new String[]{"Winged Sandals","Pendant of Swiftness"}),
      new Template(HeroItem.Slot.AMULET, "DROP_CHANCE_PCT",     new String[]{"Eye of Fortune","Relic Hunter's Idol"})
  );

  public HeroItem roll(Long ownerPlayerId, Random rnd){
    return build(ownerPlayerId, rollRarity(rnd), rnd);
  }

  /** Boss loot — guaranteed RARE, with a chance of EPIC. (Resource nodes never drop these.) */
  public HeroItem rollRare(Long ownerPlayerId, Random rnd){
    return build(ownerPlayerId, rnd.nextDouble() < 0.25 ? HeroItem.Rarity.EPIC : HeroItem.Rarity.RARE, rnd);
  }

  private HeroItem build(Long ownerPlayerId, HeroItem.Rarity rarity, Random rnd){
    int value = VALUE[rarity.ordinal()];
    Template t = TEMPLATES.get(rnd.nextInt(TEMPLATES.size()));
    HeroItem it = new HeroItem();
    it.setOwnerPlayerId(ownerPlayerId);
    it.setSlot(t.slot());
    it.setRarity(rarity);
    it.setName(t.names()[rnd.nextInt(t.names().length)]);
    Map<String,Integer> buffs = new LinkedHashMap<>();
    buffs.put(t.buff(), value);
    it.setBuffs(buffs);
    return it;
  }

  private HeroItem.Rarity rollRarity(Random rnd){
    double r = rnd.nextDouble();
    if (r < 0.05) return HeroItem.Rarity.EPIC;
    if (r < 0.30) return HeroItem.Rarity.RARE;
    return HeroItem.Rarity.COMMON;
  }
}
