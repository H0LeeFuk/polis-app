package com.polis.domain;

/**
 * Which battle layer a unit fights in. The two layers never cross-damage: a SEA attack only
 * destroys SEA-layer units (ships / aquatic units), a LAND attack only the LAND garrison.
 */
public enum CombatLayer { LAND, SEA }
