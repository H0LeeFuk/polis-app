package com.polis.api;

import java.util.Map;

/** Lightweight row for the report list. {@code role} is "ATTACKER" or "DEFENDER" for the viewer. */
public record BattleReportSummaryDTO(
    Long id,
    String foughtAt,
    String outcome,            // VICTORY | DEFEAT | DRAW — always the attacker's perspective
    String role,               // ATTACKER | DEFENDER — the requesting player's side
    Long attackerCityId,
    String attackerCityName,
    Long defenderCityId,
    String defenderCityName,
    String attackerPlayerName,
    String defenderPlayerName, // null => barbarian / unowned
    int attackerSent,
    int attackerLost,
    int defenderLost,
    Map<String,Long> resourcesStolen,
    boolean unread,
    boolean siegeStarted       // this winning assault laid a siege
){}
