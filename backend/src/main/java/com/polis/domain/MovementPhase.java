package com.polis.domain;
/**
 * OUT — outbound attack / node assault. RETURN — army (and any hero) marching home.
 * COLONY — legacy resource colony ship (superseded by SETTLE). OCCUPY — resource-node capture.
 * SETTLE — a hero marching to found a new city on an empty island slot; on arrival the
 * movement waits (arrivedAt set, not resolved) until the player picks a race.
 * SUPPORT — friendly reinforcements marching to a city (own or alliance); on arrival the troops
 * are stationed there as a {@link Reinforcement} and defend it until withdrawn or wiped out.
 */
public enum MovementPhase { OUT, RETURN, COLONY, OCCUPY, SETTLE, SUPPORT }
