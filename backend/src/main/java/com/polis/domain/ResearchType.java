package com.polis.domain;

/** Academy research. req = minimum Academy level required. */
public enum ResearchType {
  PHALANX (1, 600, 400, 500),
  SWIFT   (2, 500, 300, 700),
  BOUNTY  (2, 700, 700, 300),
  MINES   (3, 600, 800, 400),
  CATAPULT(4, 900, 900, 900);

  public final int req, costWood, costStone, costSilver;
  ResearchType(int req,int w,int s,int si){ this.req=req; this.costWood=w; this.costStone=s; this.costSilver=si; }
}
