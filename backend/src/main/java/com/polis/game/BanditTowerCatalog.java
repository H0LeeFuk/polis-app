package com.polis.game;

import com.polis.domain.HeroItem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Code-based config for the 100-level Bandit Tower: the defending force and reward at each level.
 * Kept in code (not a seed table) so the whole difficulty / reward curve is tunable in one place.
 *
 * <p>Defender power scales smoothly to a serious endgame army at level 100. Every level has an
 * "element theme" — the element its defenders most strongly RESIST — cycling FIRE→WIND→EARTH→WATER,
 * so the player benefits from attacking with a race whose element the defenders are weak to.
 */
public final class BanditTowerCatalog {
  public static final int MAX_LEVEL = 100;
  private BanditTowerCatalog(){}

  public static boolean isMilestone(int level){ return level % 10 == 0; }

  // Themed elite defender per element (seeded race specials) — gives a level its elemental character.
  private static final String[] THEME_ELITE   = { "FLAME_LEGION", "STORMCALLER", "EARTHSHAKER", "LEVIATHAN_RIDER" };
  private static final String[] THEME_ELEMENT  = { "FIRE", "WIND", "EARTH", "WATER" };

  private static int themeIndex(int level){ return (Math.max(1, level) - 1) % 4; }
  /** The element this level's defenders most resist — attack with a different element to fare better. */
  public static String resistedElement(int level){ return THEME_ELEMENT[themeIndex(level)]; }

  /** Full defending force at a level (NPC garrison), keyed by UPPERCASE unit name. */
  public static Map<String,Integer> defendersFor(int level){
    int L = Math.max(1, Math.min(MAX_LEVEL, level));
    Map<String,Integer> d = new LinkedHashMap<>();
    d.put("HOPLITE",  6 + L * 4);
    d.put("SPEARMAN", 4 + L * 3);
    d.put("ARCHER",   3 + L * 2);
    if (L >= 5)  d.put("SWORDSMAN", L * 2);
    if (L >= 8)  d.put("HORSEMAN",  2 + L / 2);
    if (L >= 15) d.put("CATAPULT",  L / 5);
    // themed elite from L10 up, growing — carries the level's elemental defence character
    if (L >= 10) d.put(THEME_ELITE[themeIndex(L)], Math.max(2, (L - 8) / 2));
    return d;
  }

  /** Reward granted on first clear of a level. resources/troops are keyed by UPPERCASE name. */
  public record Reward(Map<String,Long> resources, Map<String,Integer> troops,
                       HeroItem.Rarity itemRarity, String headline){}

  public static Reward rewardFor(int level){
    int L = Math.max(1, Math.min(MAX_LEVEL, level));
    Map<String,Long> res = new LinkedHashMap<>();
    Map<String,Integer> troops = new LinkedHashMap<>();
    HeroItem.Rarity item = null;
    String headline;

    if (isMilestone(L)){
      long amt = 1500L * L;                       // large resource cache
      res.put("WOOD", amt); res.put("STONE", amt); res.put("WHEAT", amt);
      troops.put("SWORDSMAN", L);                 // a bigger troop grant
      // hero items at the higher milestones, rarity rising toward 100
      if (L == 100)      item = HeroItem.Rarity.LEGENDARY;
      else if (L >= 80)  item = HeroItem.Rarity.EPIC;
      else if (L >= 50)  item = HeroItem.Rarity.RARE;
      headline = item != null ? "Milestone cache + " + rarityWord(item) + " hero item" : "Milestone cache";
    } else if (L % 2 == 0){
      long amt = 200L * L;
      res.put("WOOD", amt); res.put("STONE", amt); res.put("WHEAT", amt);
      headline = "Resources";
    } else {
      troops.put("SPEARMAN", 2 + L / 3);
      headline = "Troops";
    }
    return new Reward(res, troops, item, headline);
  }

  private static String rarityWord(HeroItem.Rarity r){
    return r.name().charAt(0) + r.name().substring(1).toLowerCase();
  }
}
