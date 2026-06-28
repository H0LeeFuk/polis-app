package com.polis.game;

import com.polis.domain.HeroItem;
import com.polis.domain.HeroItem.Rarity;
import org.springframework.stereotype.Service;

import java.util.*;

/** Rolls hero equipment from the seeded {@link HeroItemCatalog}. Drops are rarity-weighted. */
@Service
public class ItemFactory {

  /** Drop rarity weights for generic (node) drops — legendaries are rare. */
  private static final Map<Rarity,Integer> WEIGHT = Map.of(
      Rarity.COMMON, 60, Rarity.RARE, 28, Rarity.EPIC, 10, Rarity.LEGENDARY, 2);

  /** Copy a catalog entry into a fresh, owned inventory item. */
  private HeroItem build(Long ownerPlayerId, HeroItemCatalog.Entry e){
    HeroItem it = new HeroItem();
    it.setOwnerPlayerId(ownerPlayerId);
    it.setName(e.name());
    it.setSlot(e.slot());
    it.setRarity(e.rarity());
    it.setBuffs(new LinkedHashMap<>(e.buffs()));
    // deep-ish copy of the special-effect list so inventory rows don't share catalog maps
    List<Map<String,Object>> fx = new ArrayList<>();
    for (Map<String,Object> m : e.specialEffects()) fx.add(new LinkedHashMap<>(m));
    it.setSpecialEffects(fx);
    return it;
  }

  /** A generic drop: rarity by weight, then a random catalog item of that rarity. */
  public HeroItem roll(Long ownerPlayerId, Random rnd){
    return ofRarity(ownerPlayerId, rollRarity(rnd), rnd);
  }

  /** Boss loot — guaranteed RARE, with a chance of EPIC. */
  public HeroItem rollRare(Long ownerPlayerId, Random rnd){
    return ofRarity(ownerPlayerId, rnd.nextDouble() < 0.25 ? Rarity.EPIC : Rarity.RARE, rnd);
  }

  /** Build a random catalog item of a specific rarity (used by boss drops with their own table). */
  public HeroItem ofRarity(Long ownerPlayerId, Rarity rarity, Random rnd){
    List<HeroItemCatalog.Entry> pool = HeroItemCatalog.ofRarity(rarity);
    if (pool.isEmpty()) pool = HeroItemCatalog.ALL;
    return build(ownerPlayerId, pool.get(rnd.nextInt(pool.size())));
  }

  private Rarity rollRarity(Random rnd){
    int total = WEIGHT.values().stream().mapToInt(Integer::intValue).sum();
    int r = rnd.nextInt(total);
    for (Rarity rarity : Rarity.values()){
      r -= WEIGHT.getOrDefault(rarity, 0);
      if (r < 0) return rarity;
    }
    return Rarity.COMMON;
  }
}
