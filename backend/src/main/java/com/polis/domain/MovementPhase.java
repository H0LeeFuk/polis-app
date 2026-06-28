package com.polis.domain;
/**
 * OUT — outbound attack / node assault. RETURN — army (and any hero) marching home.
 * COLONY — legacy resource colony ship (superseded by SETTLE). OCCUPY — resource-node capture.
 * SETTLE — a hero marching to found a new city on an empty island slot; on arrival the
 * movement waits (arrivedAt set, not resolved) until the player picks a race.
 */
public enum MovementPhase { OUT, RETURN, COLONY, OCCUPY, SETTLE }
