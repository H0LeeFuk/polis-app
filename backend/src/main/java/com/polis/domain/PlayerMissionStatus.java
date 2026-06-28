package com.polis.domain;
/** LOCKED → ACTIVE (tracking) → COMPLETED (target met) → CLAIMED (rewards taken). */
public enum PlayerMissionStatus { LOCKED, ACTIVE, COMPLETED, CLAIMED }
