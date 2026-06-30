package com.polis.domain;

/** Where the account hero is and whether it can act. SETTLING = waiting at an empty island
 *  slot to found a city, pending the player's race choice. BESIEGING = locked in an active
 *  siege for its full duration (counts toward the siege's defense; freed on conquest/break). */
public enum HeroState { IDLE, MARCHING, SETTLING, WOUNDED, BESIEGING }
