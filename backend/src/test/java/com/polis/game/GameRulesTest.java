package com.polis.game;

import com.polis.domain.BuildingType;
import com.polis.domain.UnitType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pure balance-math unit tests. GameRules is fully static/deterministic. TIME_SCALE defaults to
 * 1.0 in tests (no POLIS_TIME_SCALE env), so fast(x) == x.
 */
class GameRulesTest {

  @Test void farmPop_is_75_per_level_and_zero_at_zero(){
    assertThat(GameRules.farmPop(0)).isZero();
    assertThat(GameRules.farmPop(1)).isEqualTo(75);
    assertThat(GameRules.farmPop(3)).isEqualTo(225);
  }

  @Test void citySlots_and_maxCities_cap_at_20(){
    assertThat(GameRules.citySlots(0)).isEqualTo(1);
    assertThat(GameRules.citySlots(5)).isEqualTo(6);
    assertThat(GameRules.maxCities(5)).isEqualTo(5);
    assertThat(GameRules.maxCities(25)).isEqualTo(20);   // hard cap
  }

  @Test void storeCap_grows_from_1000_to_20000(){
    assertThat(GameRules.storeCap(0)).isEqualTo(1000);
    assertThat(GameRules.storeCap(25)).isEqualTo(20000);
    assertThat(GameRules.storeCap(10)).isGreaterThan(1000).isLessThan(20000);
  }

  @Test void buildSeconds_speeds_up_with_senate_up_to_75pct(){
    int base = GameRules.buildSeconds(BuildingType.BARRACKS, 0, 0);
    assertThat(base).isEqualTo(22);                                    // baseTime, no senate discount
    int maxSenate = GameRules.buildSeconds(BuildingType.BARRACKS, 0, 30);
    assertThat(maxSenate).isEqualTo(6);                               // 22 * (1 - 0.75) = 5.5 -> 6
    // higher building level costs strictly more time
    assertThat(GameRules.buildSeconds(BuildingType.BARRACKS, 5, 0)).isGreaterThan(base);
  }

  @Test void unitSeconds_speeds_up_3pct_per_level_capped_at_50pct(){
    UnitType u = new UnitType();
    u.setTrainSeconds(20);
    assertThat(GameRules.unitSeconds(u, 0)).isEqualTo(20);
    assertThat(GameRules.unitSeconds(u, 10)).isEqualTo(14);           // 20 * (1 - 0.30)
    assertThat(GameRules.unitSeconds(u, 20)).isEqualTo(10);           // capped at 50%
    assertThat(GameRules.unitSeconds(u, 40)).isEqualTo(10);           // still capped
  }

  @Test void wonderLevelCost_is_40k_times_level_and_MAX_out_of_range(){
    assertThat(GameRules.wonderLevelCost(1)).isEqualTo(40_000L);
    assertThat(GameRules.wonderLevelCost(10)).isEqualTo(400_000L);
    assertThat(GameRules.wonderLevelCost(0)).isEqualTo(Long.MAX_VALUE);
    assertThat(GameRules.wonderLevelCost(11)).isEqualTo(Long.MAX_VALUE);
  }

  @Test void weakTargetLossMult_no_penalty_when_close_then_scales_to_3x(){
    assertThat(GameRules.weakTargetLossMult(1.0)).isEqualTo(1.0);
    assertThat(GameRules.weakTargetLossMult(1.2)).isEqualTo(1.0);     // boundary: still no penalty
    assertThat(GameRules.weakTargetLossMult(2.2)).isCloseTo(1.4, within(1e-9));   // 1 + (2.2-1.2)*0.4
    assertThat(GameRules.weakTargetLossMult(100)).isEqualTo(3.0);     // hard cap
  }

  @Test void cultureForLevel_table_and_bounds(){
    assertThat(GameRules.cultureForLevel(1)).isZero();
    assertThat(GameRules.cultureForLevel(2)).isEqualTo(2);
    assertThat(GameRules.cultureForLevel(20)).isEqualTo(120);
    assertThat(GameRules.cultureForLevel(21)).isEqualTo(Integer.MAX_VALUE);
  }

  @Test void levelReq_grows_linearly(){
    assertThat(GameRules.levelReq(0)).isEqualTo(60);
    assertThat(GameRules.levelReq(1)).isEqualTo(120);
  }
}
