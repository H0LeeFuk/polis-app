package com.polis.domain;

/** Lifecycle of a city siege. ACTIVE → SUCCEEDED (8h elapsed → conquest) or BROKEN (a lock hit 0). */
public enum SiegeStatus { ACTIVE, SUCCEEDED, BROKEN }
