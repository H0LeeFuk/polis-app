package com.polis.domain;

/**
 * The damage type an offensive unit deals. BLUNT/SHARP/DISTANCE are matched against the
 * defender's corresponding defence; SIEGE bypasses the troop formula and damages buildings.
 */
public enum AttackType { BLUNT, SHARP, DISTANCE, SIEGE }
