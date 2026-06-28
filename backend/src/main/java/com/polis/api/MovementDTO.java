package com.polis.api;

import java.util.Map;

/**
 * Client-facing view of a troop movement, framed relative to the requesting player.
 *
 * type    — ATTACK | RETURN | COLONY (SUPPORT reserved for future friendly reinforcements)
 * status  — TRAVELLING | RETURNING
 * hostile — true when this is an enemy army inbound to one of the viewer's cities
 * unitsKnown — false hides the composition (no spy report on hostile incoming); units is null then
 * loot    — only populated for the viewer's own returning armies
 * departAt/arriveAt — ISO-8601 UTC; the client computes the live countdown and progress bar
 */
public record MovementDTO(
    Long id,
    String type,
    String status,
    Long originCityId,
    String originCity,
    Long targetCityId,
    String targetCity,
    String owner,
    boolean mine,
    boolean hostile,
    boolean unitsKnown,
    Map<String,Integer> units,
    Map<String,Long> loot,
    String departAt,
    String arriveAt
){}
