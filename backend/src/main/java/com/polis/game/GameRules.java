package com.polis.game;

import com.polis.domain.BuildingType;
import com.polis.domain.UnitType;
import java.util.Map;

/** Pure functions for the game economy. No state, no Spring — trivially unit-testable. */
public final class GameRules {
  private GameRules(){}

  // ~75 pop per Farm level → 3000 at max (level 40); buildings cost ~1000 pop maxed, leaving ~2000 free.
  public static int farmPop(int level){ return level<=0 ? 0 : 75*level; }
  // TEST MODE: huge storage cap so resources never block building/training.
  public static long storeCap(int level){ return 2_000_000_000L; }
  public static double prodPerHour(int level){ return level<=0 ? 5 : Math.round(28*Math.pow(1.21, level-1)); }

  public static long[] buildCost(BuildingType b, int level){
    double m=Math.pow(b.mul, level);
    return new long[]{ Math.round(b.baseWood*m), Math.round(b.baseStone*m), Math.round(b.baseWheat*m) };
  }
  public static int buildSeconds(BuildingType b, int level, int senateLevel){
    double t=b.baseTime*Math.pow(1.28, level);
    double speed=1-Math.min(0.6, senateLevel*0.04);
    return (int)Math.max(3, Math.round(t*speed));
  }
  public static int unitSeconds(UnitType u, int fromBuildingLevel){
    return (int)Math.max(3, Math.round(u.getTrainSeconds()*(1-Math.min(0.5,(fromBuildingLevel-1)*0.03))));
  }
  public static int buildingPoints(int level){ return level*(level+1)/2; }
  public static int cityPoints(Map<BuildingType,Integer> levels){
    int p=0; for(int lv: levels.values()) p+=buildingPoints(lv); return p;
  }
  public static int levelReq(int level){ return 60+level*60; }
  public static int citySlots(int level){ return level+1; }

  /** Fixed number of city plots on every island. */
  public static final int SLOTS_PER_ISLAND = 12;
}
