package com.polis.game;

/** Snapshot of how the hero affected a battle, for the Battle Report. Null => no hero present. */
public record HeroParticipation(
    String name, int level,
    int attackBonusPct, int lossReductionPct,
    String skillUsed,        // skill armed for this fight, or null
    int xpGained, Integer leveledTo, boolean wounded
){}
