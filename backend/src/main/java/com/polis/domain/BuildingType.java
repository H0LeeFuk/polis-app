package com.polis.domain;

/** Static catalog of buildings, mirroring the game's balance. */
public enum BuildingType {
  //         wood stone wheat  mul  time(s) max pop  produces
  SENATE    (120, 100,  80,   1.5,  20,   30,  4, null),
  TIMBER    ( 90,  60,  40,   1.6,  15,   40,  3, ResourceType.WOOD),
  QUARRY    ( 80,  90,  40,   1.6,  15,   40,  3, ResourceType.STONE),
  MINE      (100,  80,  60,   1.6,  18,   40,  3, ResourceType.WHEAT),   // the city's wheat farm
  // Race special-resource extractor (Coal mine / Iron mine / Crystal grove / Pearl bed).
  // produces == null here: it yields the city RACE's special resource (resolved at production).
  EXTRACTOR (140, 120, 100,   1.6,  20,   40,  3, null),
  FARM      ( 80,  60,  40,   1.5,  16,   40,  0, null),
  WAREHOUSE ( 70,  90,  40,   1.5,  14,   25,  1, null),
  BARRACKS  (150, 120,  90,   1.5,  22,   30,  4, null),
  HARBOR    (180, 180, 130,   1.5,  24,   30,  4, null),
  MARKET    (120, 120,  90,   1.5,  18,   20,  2, null),
  LIBRARY   (220, 200, 200,   1.5,  28,   20,  3, null),
  // Watchtower: drives espionage — higher level = better chance to spy enemies and to catch their spies.
  WATCHTOWER(110,  90,  70,   1.5,  20,   20,  2, null),
  // Altar: hosts Festivals that produce account-wide Culture Points (→ levels → city slots).
  ALTAR     (200, 240, 160,   1.5,  26,   20,  3, null);

  public final int baseWood, baseStone, baseWheat;
  public final double mul;
  public final int baseTime, max, pop;
  public final ResourceType produces;

  BuildingType(int w,int s,int wh,double mul,int t,int max,int pop,ResourceType produces){
    this.baseWood=w; this.baseStone=s; this.baseWheat=wh; this.mul=mul;
    this.baseTime=t; this.max=max; this.pop=pop; this.produces=produces;
  }
}
