import { useEffect, useRef, useState } from "react";
import { getCityMovements, previewAttack } from "./api";
import type { Movement, AttackPreview, Hero } from "./types";
import { CityReportsList } from "./components/BattleReports";

// ---- formatting helpers (all countdowns computed client-side from arriveAt) ----

const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

export const UNIT_GLYPH: Record<string, string> = {
  HOPLITE: "🛡", SWORDSMAN: "⚔", SPEARMAN: "🔱", ARCHER: "🏹", HORSEMAN: "🐎", CATAPULT: "🪨", TRIREME: "⛵",
  // Giants
  BOULDER_THROWER: "🪨", TROLL: "👹", STONE_GIANT: "🗿", COLOSSUS: "🏛", WAR_BARGE: "🚢",
  // Fairies
  SPRITE: "✨", PIXIE_ARCHER: "🧚", GLIMMER_GUARD: "🛡", MOTH_RIDER: "🦋", DRAGONFLY_SKIFF: "🪰",
  // Newts
  MUDLING: "🦎", NEWT_SPEAR: "🔱", SNAPPER: "🐊", TIDE_RAIDER: "🌊", LEVIATHAN: "🐉",
};
const glyph = (t: string) => UNIT_GLYPH[t?.toUpperCase()] ?? "⚔";

export type UnitAttackType = "BLUNT" | "SHARP" | "DISTANCE" | "SIEGE";
export interface UnitDex { attackType: UnitAttackType; atk: number; defBlunt: number; defSharp: number; defDistance: number; speed: number; carry: number; pop: number; }
/** Client-side mirror of the seeded unit_types catalog, for stat tooltips (display only). */
export const UNIT_DEX: Record<string, UnitDex> = {
  HOPLITE:   { attackType: "BLUNT",    atk: 16,  defBlunt: 80, defSharp: 50, defDistance: 40, speed: 30, carry: 10, pop: 1 },
  SWORDSMAN: { attackType: "BLUNT",    atk: 55,  defBlunt: 45, defSharp: 25, defDistance: 20, speed: 22, carry: 20, pop: 1 },
  SPEARMAN:  { attackType: "SHARP",    atk: 50,  defBlunt: 30, defSharp: 75, defDistance: 35, speed: 22, carry: 25, pop: 1 },
  ARCHER:    { attackType: "DISTANCE", atk: 60,  defBlunt: 25, defSharp: 40, defDistance: 70, speed: 18, carry: 30, pop: 1 },
  HORSEMAN:  { attackType: "SHARP",    atk: 110, defBlunt: 30, defSharp: 25, defDistance: 35, speed: 12, carry: 80, pop: 4 },
  CATAPULT:  { attackType: "SIEGE",    atk: 200, defBlunt: 20, defSharp: 20, defDistance: 20, speed: 60, carry: 30, pop: 8 },
};
export const ATTACK_GLYPH: Record<string, string> = { BLUNT: "🔨", SHARP: "🗡", DISTANCE: "🏹", SIEGE: "💥" };
const titleCaseU = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

export const HERO_GLYPH = (h: { heroKey?: string }) => h.heroKey === "CELINE" ? "🧚" : "🛡";

/** Pick which (if any) idle hero leads this action: None / Leo / Celine. */
export function HeroPicker({ heroes, value, onChange }: {
  heroes: Hero[]; value: number | null; onChange: (id: number | null) => void;
}) {
  if (heroes.length === 0) return null;
  return (
    <div className="hero-picker">
      <span className="hp-label">⚔ Lead with a hero</span>
      <div className="hp-opts">
        <button type="button" className={"hp-chip" + (value === null ? " sel" : "")} onClick={() => onChange(null)}>None</button>
        {heroes.map(h => {
          const armed = h.armedSkill ? h.armedSkill.replace("_", " ") : null;
          return (
            <button type="button" key={h.id} className={"hp-chip" + (value === h.id ? " sel" : "")} onClick={() => onChange(h.id)}
              title={`+${h.bonuses.attackPct}% atk · +${h.bonuses.lootPct}% loot · −${h.bonuses.travelPct}% travel${armed ? ` · ${armed} armed` : ""}`}>
              {HERO_GLYPH(h)} {h.name} <small>Lv{h.level}</small>
            </button>
          );
        })}
      </div>
    </div>
  );
}

/** Hoverable/tappable troop chip that reveals a stat card (attack type, 3 defences, speed, carry, pop). */
export function UnitTooltip({ type, children }: { type: string; children: React.ReactNode }) {
  const d = UNIT_DEX[type?.toUpperCase()];
  return (
    <span className="unit-tip">
      {children}
      {d && (
        <span className="unit-tip-card">
          <span className="utc-head">{glyph(type)} {titleCaseU(type)}</span>
          <span className="utc-row"><span>{ATTACK_GLYPH[d.attackType]} Attack ({titleCaseU(d.attackType)})</span><b>{d.atk}</b></span>
          <span className="utc-def">
            <span title="Blunt defence">🔨 {d.defBlunt}</span>
            <span title="Sharp defence">🗡 {d.defSharp}</span>
            <span title="Distance defence">🏹 {d.defDistance}</span>
          </span>
          <span className="utc-row"><span>🐢 Speed</span><b>{d.speed} min/tile</b></span>
          <span className="utc-row"><span>🎒 Carry</span><b>{d.carry}</b></span>
          <span className="utc-row"><span>👥 Population</span><b>{d.pop}</b></span>
        </span>
      )}
    </span>
  );
}

/** Relative ETA like "4h 12m", "12m 30s", "8s". Clamps to 0. */
export function fmtEta(arriveAt: string, now: number): string {
  let s = Math.max(0, Math.round((new Date(arriveAt).getTime() - now) / 1000));
  if (s <= 0) return "arriving…";
  const h = Math.floor(s / 3600); s -= h * 3600;
  const m = Math.floor(s / 60); const sec = s - m * 60;
  if (h > 0) return `${h}h ${m.toString().padStart(2, "0")}m`;
  if (m > 0) return `${m}m ${sec.toString().padStart(2, "0")}s`;
  return `${sec}s`;
}

/** Absolute arrival like "Today 18:34" or "Jul 2 18:34". */
export function fmtArrival(arriveAt: string): string {
  const d = new Date(arriveAt);
  const hm = d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  const today = new Date();
  const sameDay = d.getDate() === today.getDate() && d.getMonth() === today.getMonth() && d.getFullYear() === today.getFullYear();
  if (sameDay) return `Today ${hm}`;
  return `${d.toLocaleDateString([], { month: "short", day: "numeric" })} ${hm}`;
}

/** Journey completion 0..100. */
export function progressPct(departAt: string, arriveAt: string, now: number): number {
  const dep = new Date(departAt).getTime(), arr = new Date(arriveAt).getTime();
  if (arr <= dep) return 100;
  return Math.max(0, Math.min(100, Math.round(((now - dep) / (arr - dep)) * 100)));
}

export function troopSummary(units: Record<string, number> | null): { glyph: string; type: string; n: number }[] {
  if (!units) return [];
  return Object.entries(units).filter(([, n]) => n > 0).map(([type, n]) => ({ glyph: glyph(type), type, n }));
}

/** Classify a movement relative to a city/player for colour + label. */
export function moveKind(m: Movement): "incoming" | "return" | "attack" | "colony" | "support" | "settle" {
  if (m.hostile) return "incoming";
  if (m.type === "RETURN") return "return";
  if (m.type === "COLONY") return "colony";
  if (m.type === "SETTLE") return "settle";
  if (m.type === "SUPPORT") return "support";
  return "attack";
}

const KIND_META: Record<string, { cls: string; icon: string; label: string }> = {
  incoming: { cls: "mv-incoming", icon: "⚠", label: "Incoming attack" },
  attack:   { cls: "mv-attack",   icon: "⚔", label: "Attack" },
  return:   { cls: "mv-return",   icon: "↩", label: "Returning" },
  colony:   { cls: "mv-colony",   icon: "🚢", label: "Colony ship" },
  settle:   { cls: "mv-colony",   icon: "🏛", label: "Founding a city" },
  support:  { cls: "mv-support",  icon: "🤝", label: "Support" },
};
export const kindMeta = (m: Movement) => KIND_META[moveKind(m)];

// ============================================================================
// AREA 1 — Travel-time preview card (inside the attack modal)
// ============================================================================

export function TravelPreview({ originCityId, targetCityId, units }: {
  originCityId: number; targetCityId: number; units: Record<string, number>;
}) {
  const [data, setData] = useState<AttackPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const total = Object.values(units).reduce((a, b) => a + (b > 0 ? b : 0), 0);

  useEffect(() => {
    if (total <= 0) { setData(null); return; }
    setLoading(true);
    const sel = Object.fromEntries(Object.entries(units).filter(([, n]) => n > 0));
    const h = window.setTimeout(() => {
      previewAttack(originCityId, targetCityId, sel)
        .then(d => setData(d)).catch(() => setData(null)).finally(() => setLoading(false));
    }, 300); // debounce while the player tweaks quantities
    return () => window.clearTimeout(h);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [originCityId, targetCityId, JSON.stringify(units)]);

  if (total <= 0) return null;
  return (
    <div className="travel-card">
      {!data ? (
        <div className="travel-row muted">{loading ? "Charting the route…" : "—"}</div>
      ) : (
        <>
          <div className="travel-row"><span>🐢 Slowest unit</span><b>{data.slowestUnit ? titleCase(data.slowestUnit) : "—"}</b></div>
          <div className="travel-row"><span>📏 Distance</span><b>{data.distance} tiles</b></div>
          <div className="travel-row"><span>⏱ Travel time</span><b>{fmtDuration(data.travelSeconds)}</b></div>
          <div className="travel-row"><span>🕐 Arrives</span><b>{fmtArrival(data.arriveAt)}</b></div>
        </>
      )}
    </div>
  );
}

function fmtDuration(s: number): string {
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  if (h > 0) return `${h}h ${m.toString().padStart(2, "0")}m`;
  if (m > 0) return `${m}m ${sec.toString().padStart(2, "0")}s`;
  return `${sec}s`;
}

// ============================================================================
// AREA 2 — City movements panel (inside the city view)
// ============================================================================

type CityTab = "outgoing" | "incoming" | "returning" | "reports";

export function CityMovementsPanel({ cityId, now, onAttackAgain }: {
  cityId: number; now: number;
  onAttackAgain?: (targetCityId: number, targetCityName: string) => void;
}) {
  const [moves, setMoves] = useState<Movement[] | null>(null);
  const [tab, setTab] = useState<CityTab>("outgoing");
  const [open, setOpen] = useState(true);

  useEffect(() => {
    let alive = true;
    const load = () => getCityMovements(cityId).then(m => { if (alive) setMoves(m); }).catch(() => {});
    load();
    const t = window.setInterval(load, 30000); // auto-refresh per spec
    return () => { alive = false; window.clearInterval(t); };
  }, [cityId]);

  const all = moves ?? [];
  // armies leaving this city; armies marching home; hostile armies inbound
  const outgoing = all.filter(m => !m.hostile && (m.type === "ATTACK" || m.type === "COLONY" || m.type === "SETTLE"));
  const returning = all.filter(m => !m.hostile && m.type === "RETURN");
  const incoming = all.filter(m => m.hostile);
  const activeCount = all.length;

  const tabs: { id: CityTab; label: string; n: number }[] = [
    { id: "outgoing", label: "Outgoing", n: outgoing.length },
    { id: "incoming", label: "Incoming", n: incoming.length },
    { id: "returning", label: "Returning", n: returning.length },
    { id: "reports", label: "Reports", n: 0 },
  ];
  const shown = tab === "outgoing" ? outgoing : tab === "incoming" ? incoming : returning;

  return (
    <div className={"city-moves" + (open ? "" : " collapsed")}>
      <button className="cm-head" onClick={() => setOpen(o => !o)}>
        <span>🪖 Movements{activeCount > 0 ? ` · ${activeCount}` : ""}</span>
        <span className={"cm-chevron" + (incoming.length ? " threat" : "")}>{open ? "▾" : "▸"}</span>
      </button>
      {open && (
        <>
          <div className="cm-tabs">
            {tabs.map(t => (
              <button key={t.id} className={"cm-tab" + (tab === t.id ? " active" : "") + (t.id === "incoming" && t.n > 0 ? " threat" : "")}
                onClick={() => setTab(t.id)}>{t.label}{t.n > 0 ? ` (${t.n})` : ""}</button>
            ))}
          </div>
          <div className="cm-list">
            {tab === "reports"
              ? <CityReportsList cityId={cityId} onAttackAgain={onAttackAgain} />
              : shown.length === 0
                ? <EmptyMovements />
                : shown.map(m => <MovementRow key={m.id} m={m} now={now} />)}
          </div>
        </>
      )}
    </div>
  );
}

function MovementRow({ m, now }: { m: Movement; now: number }) {
  const meta = kindMeta(m);
  const pct = progressPct(m.departAt, m.arriveAt, now);
  const troops = troopSummary(m.units);
  const lootEntries = m.loot ? Object.entries(m.loot).filter(([, v]) => v > 0) : [];
  return (
    <div className={"mv-row " + meta.cls}>
      <div className="mv-top">
        <span className="mv-icon">{meta.icon}</span>
        <span className="mv-route">
          {m.type === "RETURN" ? <>{m.originCity} → <b>{m.targetCity}</b></> : <><b>{m.targetCity}</b></>}
          {m.hostile && <small className="muted"> from {m.owner}</small>}
        </span>
        <span className="mv-eta">{fmtEta(m.arriveAt, now)}</span>
      </div>
      <div className="mv-sub">
        <span className="mv-label">{meta.label}</span>
        {m.unitsKnown
          ? (troops.length
              ? <span className="mv-troops">{troops.map(t => <span key={t.type} title={t.type}>{t.glyph}{t.n}</span>)}</span>
              : <span className="muted">—</span>)
          : <span className="muted">Unknown forces</span>}
      </div>
      {lootEntries.length > 0 && (
        <div className="mv-loot">🎒 {lootEntries.map(([k, v]) => `${Math.round(v)} ${titleCase(k)}`).join(" · ")}</div>
      )}
      <div className="mv-bar"><i className={meta.cls} style={{ width: pct + "%" }} /></div>
    </div>
  );
}

function EmptyMovements() {
  return (
    <div className="mv-empty">
      <svg viewBox="0 0 48 48" width="40" height="40" aria-hidden>
        <path d="M8 28 C8 16 16 9 24 9 C32 9 40 16 40 28 L40 30 L8 30 Z" fill="#c8a24e" stroke="#7a5a22" strokeWidth="2" strokeLinejoin="round" />
        <path d="M24 5 C26 5 27 8 24 11 C21 8 22 5 24 5 Z" fill="#d2603a" stroke="#7a5a22" strokeWidth="1.5" />
        <rect x="6" y="29" width="36" height="4" rx="2" fill="#7a5a22" />
        <path d="M24 11 L24 30" stroke="#7a5a22" strokeWidth="2" />
      </svg>
      <p className="muted">No active movements — your armies rest.</p>
    </div>
  );
}
