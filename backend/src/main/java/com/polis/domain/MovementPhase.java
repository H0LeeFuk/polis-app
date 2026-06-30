package com.polis.domain;
/**
 * OUT — outbound attack / node assault. RETURN — army (and any hero) marching home.
 * COLONY — legacy resource colony ship (superseded by SETTLE). OCCUPY — resource-node capture.
 * SETTLE — a hero marching to found a new city on an empty island slot; on arrival the
 * movement waits (arrivedAt set, not resolved) until the player picks a race.
 * SUPPORT — friendly reinforcements marching to a city (own or alliance); on arrival the troops
 * are stationed there as a {@link Reinforcement} and defend it until withdrawn or wiped out.
 * SIEGE_REINFORCE — attacker/ally troops marching to join an active siege (added to the besieging
 * force on arrival; never resets the 8h clock). SIEGE_ATTACK — the city owner/ally marching to
 * break a siege (resolved against the besieging force's matching layer on arrival).
 */
public enum MovementPhase { OUT, RETURN, COLONY, OCCUPY, SETTLE, SUPPORT, SIEGE_REINFORCE, SIEGE_ATTACK }
