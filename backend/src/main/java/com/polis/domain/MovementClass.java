package com.polis.domain;

/**
 * How a unit crosses the map.
 * <ul>
 *   <li>{@code LAND} — cannot cross open water on its own; must be ferried by a transport
 *       (a SWIMMING unit with {@code transportCapacity > 0}) when a route spans two islands.</li>
 *   <li>{@code FLYING} — crosses any terrain freely at its own (usually fast) pace.</li>
 *   <li>{@code SWIMMING} — crosses water freely at sea pace, but is penalised on land
 *       (see {@code TravelTimeService.LAND_PENALTY}). Transports are SWIMMING.</li>
 * </ul>
 */
public enum MovementClass { LAND, FLYING, SWIMMING }
