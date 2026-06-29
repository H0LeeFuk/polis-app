package com.polis.domain;

/**
 * Tradeable city resources. Base resources (STONE/WOOD/WHEAT) are produced by every city and
 * lootable from anyone. Special resources (COAL/CRYSTALS/IRON/PEARLS) are each produced by a
 * single race and used to train that race's elite units; they can only be looted from a city of
 * the producing race. Population and Gold are NOT resources (Gold is the premium currency).
 */
public enum ResourceType {
  WOOD(true), STONE(true), WHEAT(true),
  COAL(false), CRYSTALS(false), IRON(false), PEARLS(false);

  /** True for the three universal base resources. */
  public final boolean base;
  ResourceType(boolean base){ this.base = base; }
  public boolean isSpecial(){ return !base; }
}
