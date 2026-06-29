package com.polis.domain;

/**
 * Lifecycle of a trade convoy.
 * PENDING    — created but waiting for an outbound convoy slot to free up at the destination market
 * IN_TRANSIT — dispatched, physically travelling (departAt/arriveAt set)
 * DELIVERED  — arrived; cargo credited to the destination city
 */
public enum ConvoyStatus { PENDING, IN_TRANSIT, DELIVERED }
