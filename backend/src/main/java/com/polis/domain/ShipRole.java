package com.polis.domain;

/**
 * Role of a ship for the LAND races (Humans, Giants). Null for non-ship units — including Newt
 * aquatic units, which are SEA-layer but swim under their own power rather than being "ships".
 * <ul>
 *   <li>{@code TRANSPORT} — ferries LAND ground troops across water; minimal combat.</li>
 *   <li>{@code DEFENSE}  — guards the city harbor; high naval defence, low attack.</li>
 *   <li>{@code ATTACK}   — destroys enemy fleets; high naval attack, lower defence.</li>
 * </ul>
 */
public enum ShipRole { TRANSPORT, DEFENSE, ATTACK }
