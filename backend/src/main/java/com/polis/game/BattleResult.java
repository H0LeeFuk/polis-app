package com.polis.game;

import com.polis.domain.BattleOutcome;
import java.util.Map;

/**
 * Computed combat outcome handed from {@link TickScheduler} to {@link BattleReportService}.
 * All troop maps are keyed by {@code UnitType.name()}; resources by their {@code ResourceType}
 * name (WOOD/STONE/WHEAT + special). Element maps are keyed FIRE/WIND/EARTH/WATER.
 */
public record BattleResult(
    BattleOutcome outcome,
    Map<String,Integer> attackerSent,
    Map<String,Integer> attackerLost,
    Map<String,Integer> attackerSurvived,
    Map<String,Integer> defenderPresent,
    Map<String,Integer> defenderLost,
    Map<String,Integer> defenderSurvived,
    Map<String,Long> resourcesStolen,
    int attackerAttackPower,
    int defenderDefencePower,
    int siegeDamage,
    Map<String,Integer> attackByElement,
    Map<String,Integer> defenseByElement
){}
