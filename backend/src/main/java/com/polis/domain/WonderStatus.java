package com.polis.domain;

/**
 * Wonder lifecycle. DORMANT — inert during GROWTH (visible on the map, not contestable).
 * ACTIVE — endgame began, unclaimed and open to occupation. CONTROLLED — an alliance holds it
 * with a garrison. CONTESTED — recently lost a battle; briefly open before settling.
 */
public enum WonderStatus { DORMANT, ACTIVE, CONTROLLED, CONTESTED }
