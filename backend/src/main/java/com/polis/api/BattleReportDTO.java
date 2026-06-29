package com.polis.api;

import java.util.Map;

/** Full battle breakdown. Both sides are always present; the client renders perspective from {@code role}. */
public record BattleReportDTO(
    Long id,
    String foughtAt,
    String outcome,            // VICTORY | DEFEAT | DRAW — always the attacker's perspective
    String role,               // ATTACKER | DEFENDER — the requesting player's side
    String combatLayer,        // SEA (fleets engaged) | LAND (garrison engaged)

    Long attackerPlayerId,
    String attackerPlayerName,
    Long attackerCityId,
    String attackerCityName,

    Long defenderPlayerId,     // null => barbarian / unowned
    String defenderPlayerName,
    Long defenderCityId,
    String defenderCityName,

    Map<String,Integer> attackerTroopsSent,
    Map<String,Integer> attackerTroopsLost,
    Map<String,Integer> attackerTroopsSurvived,
    Map<String,Integer> defenderTroopsPresent,
    Map<String,Integer> defenderTroopsLost,
    Map<String,Integer> defenderTroopsSurvived,

    Map<String,Long> resourcesStolen,
    int attackerTotalAttackPower,
    int defenderTotalDefencePower,
    int siegeDamage,
    Map<String,Integer> attackByElement,
    Map<String,Integer> defenseByElement,
    int combatPointsEarned,
    String combatPointsReason,

    // hero participation — heroName null => no hero took part
    String heroName,
    int heroLevel,
    int heroAttackBonusPct,
    int heroLossReductionPct,
    String heroSkillUsed,
    int heroXpGained,
    Integer heroLeveledTo,
    boolean heroWounded,

    boolean unread
){}
