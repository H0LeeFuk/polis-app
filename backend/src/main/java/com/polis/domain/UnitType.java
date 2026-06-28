package com.polis.domain;

/** Static catalog of trainable units. */
public enum UnitType {
  //          kind        from               atk def spd pop carry time  w   s   si  research
  SLINGER  (Kind.LAND, QueueType.BARRACKS, 23,  8, 18, 1,  8,  9,   55,   0,  40, null),
  SWORDS   (Kind.LAND, QueueType.BARRACKS, 18, 38, 10, 1, 12, 13,   60,  30,  30, null),
  HOPLITE  (Kind.LAND, QueueType.BARRACKS, 32, 26, 13, 1, 14, 15,   70,  40,  55, null),
  RIDER    (Kind.LAND, QueueType.BARRACKS, 62, 30, 30, 3, 46, 24,  160, 100, 140, null),
  CATAPULT (Kind.LAND, QueueType.BARRACKS,140, 40,  8, 6, 20, 40,  380, 520, 220, ResearchType.CATAPULT),
  BIREME   (Kind.SEA,  QueueType.HARBOR,   30, 90, 14, 4,  0, 26,  240, 120, 170, null),
  TRIREME  (Kind.SEA,  QueueType.HARBOR,  110, 50, 18, 5,  0, 34,  320, 140, 260, null),
  TRANSPORT(Kind.SEA,  QueueType.HARBOR,    0, 18, 11, 4,120, 24,  200,  80,  60, null);

  public enum Kind { LAND, SEA }
  public final Kind kind;
  public final QueueType from;
  public final int atk, def, speed, pop, carry, time;
  public final int costWood, costStone, costSilver;
  public final ResearchType research;

  UnitType(Kind k, QueueType from,int atk,int def,int spd,int pop,int carry,int time,
           int w,int s,int si, ResearchType research){
    this.kind=k; this.from=from; this.atk=atk; this.def=def; this.speed=spd; this.pop=pop;
    this.carry=carry; this.time=time; this.costWood=w; this.costStone=s; this.costSilver=si;
    this.research=research;
  }
}
