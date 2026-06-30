// Player preferences for the world-map movement-lines layer. Persisted locally (frontend-only
// visualization); defaults to a single neutral colour for every movement type until customized.

export type MoveType = "ATTACK" | "RETURN" | "COLONY" | "SUPPORT" | "SETTLE" | "TRADE" | "SPY" | "OCCUPY";

export interface MapLinePrefs {
  enabled: boolean;
  opacity: number;                    // resting (default) line opacity 0..1
  colors: Record<string, string>;     // per-type override; missing => NEUTRAL
}

export const NEUTRAL_COLOR = "#9aa7bd";
export const INCOMING_COLOR = "#e8584a";   // incoming attacks always read as a threat

export const MOVE_TYPES: { type: MoveType; label: string; icon: string }[] = [
  { type: "ATTACK",  label: "Attacks",     icon: "⚔" },
  { type: "SUPPORT", label: "Support",     icon: "🛡" },
  { type: "RETURN",  label: "Returning",   icon: "↩" },
  { type: "TRADE",   label: "Trade",       icon: "🔁" },
  { type: "OCCUPY",  label: "Occupy/Node", icon: "⛏" },
  { type: "COLONY",  label: "Colony",      icon: "🚢" },
  { type: "SETTLE",  label: "Settle",      icon: "🏛" },
  { type: "SPY",     label: "Spy",         icon: "🕵" },
];

const KEY = "polis_map_lines";
const DEFAULTS: MapLinePrefs = { enabled: true, opacity: 0.35, colors: {} };

export function loadLinePrefs(): MapLinePrefs {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...DEFAULTS };
    const p = JSON.parse(raw);
    return { enabled: p.enabled ?? true, opacity: typeof p.opacity === "number" ? p.opacity : 0.35, colors: p.colors ?? {} };
  } catch { return { ...DEFAULTS }; }
}
export function saveLinePrefs(p: MapLinePrefs) {
  try { localStorage.setItem(KEY, JSON.stringify(p)); } catch { /* ignore quota / privacy mode */ }
}
/** The colour for a movement: incoming attacks are always the threat colour; otherwise the per-type override or neutral. */
export function colorFor(type: string, incoming: boolean, prefs: MapLinePrefs): string {
  if (incoming) return INCOMING_COLOR;
  return prefs.colors[type] || NEUTRAL_COLOR;
}
