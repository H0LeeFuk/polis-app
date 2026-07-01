package com.polis.game;

import com.polis.domain.BattleOutcome;
import com.polis.domain.CombatLayer;
import com.polis.domain.Element;
import com.polis.domain.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Combat resolver tests (highest-risk logic). Uses a mocked {@link UnitCatalog} so no DB is needed —
 * the engine is pure given the unit stats. Covers the undefended-target 0-loss fix, win-with-losses,
 * defeat, elemental advantage, and siege-only (cannot beat a garrison).
 */
class CombatEngineTest {
  private UnitCatalog catalog;
  private CombatEngine engine;

  private static UnitType land(int atk, Element el, boolean siege, int df, int dw, int de, int dwa){
    UnitType u = new UnitType();
    u.setAttack(atk); u.setAttackElement(el); u.setSiege(siege);
    u.setDefenseFire(df); u.setDefenseWind(dw); u.setDefenseEarth(de); u.setDefenseWater(dwa);
    u.setCombatLayer(CombatLayer.LAND);
    return u;
  }

  @BeforeEach void setUp(){
    catalog = mock(UnitCatalog.class);
    // FIRE attacker with modest defence; a sturdy neutral defender; a siege engine; a fire-weak defender.
    lenient().when(catalog.get("SWORD")).thenReturn(land(55, Element.FIRE, false, 35, 30, 40, 28));
    lenient().when(catalog.get("HOPLITE")).thenReturn(land(16, null, false, 60, 60, 60, 60));
    lenient().when(catalog.get("CATAPULT")).thenReturn(land(200, null, true, 20, 20, 20, 20));
    lenient().when(catalog.get("FIREWEAK")).thenReturn(land(10, null, false, 5, 90, 90, 90));
    lenient().when(catalog.get("FIRETANK")).thenReturn(land(10, null, false, 90, 90, 90, 90));
    engine = new CombatEngine(catalog);
  }

  @Test void undefended_target_costs_the_attacker_no_troops(){
    var r = engine.resolve(Map.of("SWORD", 100), Element.FIRE, Map.of(), CombatEngine.Mods.none());
    assertThat(r.outcome()).isEqualTo(BattleOutcome.VICTORY);
    assertThat(r.attackerLost()).isEmpty();                       // the fix: 0 losses vs empty garrison
    assertThat(r.attackerSurvived()).containsEntry("SWORD", 100);
  }

  @Test void winning_against_a_defended_target_still_costs_some_troops(){
    var r = engine.resolve(Map.of("SWORD", 200), Element.FIRE, Map.of("HOPLITE", 50), CombatEngine.Mods.none());
    assertThat(r.outcome()).isEqualTo(BattleOutcome.VICTORY);
    assertThat(r.defenderSurvived()).isEmpty();                   // defenders routed on a win
    int lost = r.attackerLost().getOrDefault("SWORD", 0);
    assertThat(lost).isGreaterThan(0).isLessThan(200);           // real casualties, not a wipe
  }

  @Test void attacker_is_wiped_when_outmatched(){
    var r = engine.resolve(Map.of("SWORD", 10), Element.FIRE, Map.of("HOPLITE", 100), CombatEngine.Mods.none());
    assertThat(r.outcome()).isEqualTo(BattleOutcome.DEFEAT);
    assertThat(r.attackerSurvived()).isEmpty();                   // attacker wiped
    assertThat(r.attackerLost()).containsEntry("SWORD", 10);
  }

  @Test void elemental_advantage_decides_an_otherwise_identical_fight(){
    // same attacker + same defender COUNT; only the defender's fire-resistance differs
    var vsWeak = engine.resolve(Map.of("SWORD", 60), Element.FIRE, Map.of("FIREWEAK", 60), CombatEngine.Mods.none());
    var vsTank = engine.resolve(Map.of("SWORD", 60), Element.FIRE, Map.of("FIRETANK", 60), CombatEngine.Mods.none());
    assertThat(vsWeak.outcome()).isEqualTo(BattleOutcome.VICTORY);   // low fire-def → attacker breaks through
    assertThat(vsTank.outcome()).isEqualTo(BattleOutcome.DEFEAT);    // high fire-def → attacker bounces
  }

  @Test void siege_only_army_cannot_beat_a_garrison_but_deals_wall_damage(){
    var r = engine.resolve(Map.of("CATAPULT", 5), Element.FIRE, Map.of("HOPLITE", 10), CombatEngine.Mods.none());
    assertThat(r.outcome()).isEqualTo(BattleOutcome.DEFEAT);       // no anti-troop attack → loses the field
    assertThat(r.siegeDamage()).isEqualTo(1000);                   // 200 * 5 wall damage tallied apart
  }
}
