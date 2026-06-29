package com.polis.domain;

/**
 * The four combat elements (PART 1: elemental combat). Each race attacks with one element
 * (Humans=FIRE, Fairies=WIND, Giants=EARTH, Newts=WATER) but every unit defends against all
 * four. Siege is NOT an element — siege units are flagged {@code isSiege} and deal wall damage
 * outside the elemental troop formula.
 */
public enum Element { FIRE, WIND, EARTH, WATER }
