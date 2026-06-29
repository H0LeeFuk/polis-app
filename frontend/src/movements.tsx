import { useEffect, useRef, useState } from "react";
import { getCityMovements, previewAttack, getReinforcements } from "./api";
import type { Movement, AttackPreview, Hero } from "./types";

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
  // Library-unlocked shared units + City Guard militia
  RAIDER: "🪓", WARDEN: "🏰", OUTRIDER: "🦅", MILITIA: "🧑‍🌾",
};
const glyph = (t: string) => UNIT_GLYPH[t?.toUpperCase()] ?? "⚔";

export type ElementType = "FIRE" | "WIND" | "EARTH" | "WATER";
export interface UnitDex { element: ElementType | null; siege?: boolean; atk: number; defFire: number; defWind: number; defEarth: number; defWater: number; speed: number; carry: number; pop: number; }
/** Client-side mirror of the seeded unit_types catalog, for stat tooltips (display only). */
export const UNIT_DEX: Record<string, UnitDex> = {
  // shared roster — element is the attacking city's race element (null here)
  HOPLITE:   { element: null, atk: 16,  defFire: 60, defWind: 55, defEarth: 75, defWater: 50, speed: 30, carry: 10, pop: 1 },
  SWORDSMAN: { element: null, atk: 55,  defFire: 35, defWind: 30, defEarth: 40, defWater: 28, speed: 22, carry: 20, pop: 1 },
  SPEARMAN:  { element: null, atk: 50,  defFire: 55, defWind: 45, defEarth: 40, defWater: 45, speed: 22, carry: 25, pop: 1 },
  ARCHER:    { element: null, atk: 60,  defFire: 40, defWind: 60, defEarth: 35, defWater: 45, speed: 18, carry: 30, pop: 1 },
  HORSEMAN:  { element: null, atk: 110, defFire: 35, defWind: 35, defEarth: 30, defWater: 30, speed: 12, carry: 80, pop: 4 },
  CATAPULT:  { element: null, siege: true, atk: 200, defFire: 20, defWind: 20, defEarth: 20, defWater: 20, speed: 60, carry: 30, pop: 8 },
  // race elite units
  FLAME_LEGION:    { element: "FIRE",  atk: 150, defFire: 90, defWind: 70, defEarth: 60, defWater: 45, speed: 20, carry: 40, pop: 3 },
  EARTHSHAKER:     { element: "EARTH", atk: 170, defFire: 60, defWind: 55, defEarth: 95, defWater: 70, speed: 26, carry: 50, pop: 4 },
  STORMCALLER:     { element: "WIND",  atk: 175, defFire: 55, defWind: 95, defEarth: 45, defWater: 65, speed: 14, carry: 35, pop: 3 },
  LEVIATHAN_RIDER: { element: "WATER", atk: 165, defFire: 60, defWind: 50, defEarth: 65, defWater: 95, speed: 18, carry: 60, pop: 4 },
  // race rosters — attack with the city race's element (null here); defences from V25.
  // Fairies (flyers)
  GLIMMER_GUARD:   { element: null, atk: 30,  defFire: 45, defWind: 65, defEarth: 40, defWater: 45, speed: 10, carry: 10,  pop: 1 },
  SPRITE:          { element: null, atk: 45,  defFire: 25, defWind: 45, defEarth: 20, defWater: 30, speed: 8,  carry: 15,  pop: 1 },
  PIXIE_ARCHER:    { element: null, atk: 80,  defFire: 20, defWind: 42, defEarth: 18, defWater: 28, speed: 7,  carry: 20,  pop: 1 },
  DRAGONFLY_SKIFF: { element: null, atk: 100, defFire: 30, defWind: 55, defEarth: 30, defWater: 35, speed: 10, carry: 260, pop: 4 },
  MOTH_RIDER:      { element: null, atk: 130, defFire: 30, defWind: 52, defEarth: 28, defWater: 34, speed: 5,  carry: 120, pop: 3 },
  // Giants (heavy)
  BOULDER_THROWER: { element: null, atk: 90,  defFire: 40, defWind: 35, defEarth: 60, defWater: 45, speed: 40, carry: 20,  pop: 3 },
  WAR_BARGE:       { element: null, atk: 120, defFire: 30, defWind: 30, defEarth: 40, defWater: 45, speed: 35, carry: 300, pop: 10 },
  TROLL:           { element: null, atk: 120, defFire: 55, defWind: 45, defEarth: 70, defWater: 50, speed: 40, carry: 60,  pop: 4 },
  STONE_GIANT:     { element: null, atk: 180, defFire: 65, defWind: 55, defEarth: 90, defWater: 60, speed: 45, carry: 40,  pop: 6 },
  COLOSSUS:        { element: null, atk: 360, defFire: 70, defWind: 60, defEarth: 100, defWater: 65, speed: 70, carry: 50, pop: 12 },
  // Humans (ship)
  TRIREME:         { element: null, atk: 90,  defFire: 45, defWind: 40, defEarth: 40, defWater: 55, speed: 20, carry: 200, pop: 6 },
  // Newts (amphibious)
  MUDLING:         { element: null, atk: 35,  defFire: 25, defWind: 25, defEarth: 30, defWater: 45, speed: 26, carry: 15,  pop: 1 },
  NEWT_SPEAR:      { element: null, atk: 60,  defFire: 40, defWind: 35, defEarth: 35, defWater: 55, speed: 24, carry: 20,  pop: 1 },
  TIDE_RAIDER:     { element: null, atk: 120, defFire: 40, defWind: 35, defEarth: 45, defWater: 65, speed: 12, carry: 300, pop: 3 },
  SNAPPER:         { element: null, atk: 140, defFire: 45, defWind: 40, defEarth: 50, defWater: 70, speed: 14, carry: 250, pop: 4 },
  LEVIATHAN:       { element: null, atk: 300, defFire: 60, defWind: 50, defEarth: 65, defWater: 92, speed: 12, carry: 400, pop: 10 },
  // Library-unlocked shared units (trainable by any race once researched) + summon-only City Guard militia
  RAIDER:          { element: null, atk: 70,  defFire: 30, defWind: 30, defEarth: 25, defWater: 25, speed: 12, carry: 60,  pop: 1 },
  WARDEN:          { element: null, atk: 30,  defFire: 75, defWind: 70, defEarth: 95, defWater: 80, speed: 22, carry: 20,  pop: 1 },
  OUTRIDER:        { element: null, atk: 90,  defFire: 30, defWind: 52, defEarth: 28, defWater: 34, speed: 10, carry: 120, pop: 3 },
  MILITIA:         { element: null, atk: 10,  defFire: 35, defWind: 35, defEarth: 35, defWater: 35, speed: 30, carry: 0,   pop: 0 },
};
export const ELEMENT_GLYPH: Record<string, string> = { FIRE: "🔥", WIND: "🌬", EARTH: "🌍", WATER: "💧" };
const titleCaseU = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

export const HERO_GLYPH = (h: { heroKey?: string }) => h.heroKey === "TITANIA" ? "🧚" : "🛡";

/** Pick which (if any) idle hero leads this action: None / Leo / Titania. */
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
          const fx = h.specialEffects && h.specialEffects.length ? ` · ${h.specialEffects.join(" · ")}` : "";
          return (
            <button type="button" key={h.id} className={"hp-chip" + (value === h.id ? " sel" : "")} onClick={() => onChange(h.id)}
              title={`+${h.bonuses.attackPct}% atk · +${h.bonuses.lootPct}% loot · −${h.bonuses.travelPct}% travel${armed ? ` · ${armed} armed` : ""}${fx}`}>
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
          <span className="utc-row"><span>{d.siege ? "💥 Siege" : d.element ? `${ELEMENT_GLYPH[d.element]} ${titleCaseU(d.element)}` : "⚔ City element"} attack</span><b>{d.atk}</b></span>
          <span className="utc-def">
            <span title="Fire defence">🔥 {d.defFire}</span>
            <span title="Wind defence">🌬 {d.defWind}</span>
            <span title="Earth defence">🌍 {d.defEarth}</span>
            <span title="Water defence">💧 {d.defWater}</span>
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
export function moveKind(m: Movement): "incoming" | "return" | "attack" | "colony" | "support" | "settle" | "trade" | "spy" {
  if (m.type === "TRADE") return "trade";
  if (m.type === "SPY") return "spy";
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
  spy:      { cls: "mv-spy",      icon: "🕵", label: "Spy mission" },
  trade:    { cls: "mv-trade",    icon: "🚚", label: "Trade convoy" },
};

const RES_GLYPH: Record<string, string> = {
  WOOD: "🪵", STONE: "🪨", WHEAT: "🌾",
  COAL: "⬛", CRYSTALS: "💎", IRON: "⛓", PEARLS: "🫧",
};
export const kindMeta = (m: Movement) => KIND_META[moveKind(m)];

// ============================================================================
// AREA 1 — Travel-time preview card (inside the attack modal)
// ============================================================================

export function TravelPreview({ originCityId, targetCityId, units, heroId, onState }: {
  originCityId: number; targetCityId: number; units: Record<string, number>;
  heroId?: number | null;
  /** Reports whether the current army can make the trip (false = transport insufficient). */
  onState?: (sufficient: boolean) => void;
}) {
  const [data, setData] = useState<AttackPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const total = Object.values(units).reduce((a, b) => a + (b > 0 ? b : 0), 0);

  useEffect(() => {
    if (total <= 0 && heroId == null) { setData(null); onState?.(true); return; }
    setLoading(true);
    const sel = Object.fromEntries(Object.entries(units).filter(([, n]) => n > 0));
    const h = window.setTimeout(() => {
      previewAttack(originCityId, targetCityId, sel, heroId)
        .then(d => { setData(d); onState?.(d.transportSufficient !== false && d.combatLayer !== "MIXED"); })
        .catch(() => { setData(null); onState?.(true); }).finally(() => setLoading(false));
    }, 300); // debounce while the player tweaks quantities
    return () => window.clearTimeout(h);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [originCityId, targetCityId, JSON.stringify(units), heroId]);

  if (total <= 0 && heroId == null) return null;
  const water = data?.routeCrossesWater;
  const insufficient = data && water && data.transportSufficient === false;
  const freeCrossing = data && water && (data.requiredTransportCapacity ?? 0) === 0;
  return (
    <div className="travel-card">
      {!data ? (
        <div className="travel-row muted">{loading ? "Charting the route…" : "—"}</div>
      ) : (
        <>
          {data.combatLayer === "MIXED" ? (
            <div className="layer-banner mixed">⚠ Mixed forces — send ships and ground troops as <b>separate attacks</b>. Sea attacks hit the enemy fleet; land attacks hit the garrison.</div>
          ) : data.combatLayer === "SEA" ? (
            <div className="layer-banner sea">⛵ <b>Sea attack</b> — engages the enemy fleet only. Clear it before landing troops.</div>
          ) : data.combatLayer === "LAND" ? (
            <div className="layer-banner land">⚔ <b>Land attack</b> — engages the enemy garrison only (ships untouched).</div>
          ) : null}
          <div className="travel-row"><span>🐢 Slowest unit</span><b>{data.slowestUnit ? titleCase(data.slowestUnit) : "—"}</b></div>
          <div className="travel-row"><span>📏 Distance</span><b>{data.distance} tiles</b></div>
          <div className="travel-row"><span>⏱ Travel time</span><b>{fmtDuration(data.travelSeconds)}</b></div>
          <div className="travel-row"><span>🕐 Arrives</span><b>{fmtArrival(data.arriveAt)}</b></div>
          {water && (
            <div className={"transport-panel" + (insufficient ? " bad" : " ok")}>
              {freeCrossing ? (
                <div className="tp-line">🌊 Crosses water freely — no transport needed.</div>
              ) : (
                <>
                  <div className="tp-line">
                    🚢 Transport: <b>{data.providedTransportCapacity ?? 0}</b> / {data.requiredTransportCapacity ?? 0} land-pop capacity
                  </div>
                  {insufficient && (
                    <div className="tp-warn">⚠ {data.transportWarning
                      ?? "Not enough transport to cross water."}{(data.transportShipsShort ?? 0) > 0
                        ? ` Add ~${data.transportShipsShort} more transport ship(s).` : ""}</div>
                  )}
                </>
              )}
            </div>
          )}
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

type CityTab = "outgoing" | "incoming" | "returning";

export function CityMovementsPanel({ cityId, now, onExpand, reloadKey }: {
  cityId: number; now: number;
  onAttackAgain?: (targetCityId: number, targetCityName: string) => void;
  onExpand?: () => void;
  /** bump to force an immediate reload (e.g. right after dispatching an attack/support) */
  reloadKey?: number;
}) {
  const [moves, setMoves] = useState<Movement[] | null>(null);
  const [reinf, setReinf] = useState<{ owner: string; mine: boolean; units: Record<string, number> }[]>([]);
  const [tab, setTab] = useState<CityTab>("outgoing");
  const [open, setOpen] = useState(true);

  useEffect(() => {
    let alive = true;
    const load = () => {
      getCityMovements(cityId).then(m => { if (alive) setMoves(m); }).catch(() => {});
      getReinforcements(cityId).then(r => { if (alive) setReinf(r); }).catch(() => {});
    };
    load();
    const t = window.setInterval(load, 30000); // auto-refresh per spec
    return () => { alive = false; window.clearInterval(t); };
  }, [cityId, reloadKey]);

  const all = moves ?? [];
  // armies/convoys leaving this city; armies marching home; hostile armies inbound
  const outgoing = all.filter(m => !m.hostile && (m.type === "ATTACK" || m.type === "COLONY" || m.type === "SETTLE" || m.type === "TRADE" || m.type === "SUPPORT" || m.type === "SPY"));
  const returning = all.filter(m => !m.hostile && m.type === "RETURN");
  const incoming = all.filter(m => m.hostile);
  const activeCount = all.length;

  const tabs: { id: CityTab; label: string; n: number }[] = [
    { id: "outgoing", label: "Outgoing", n: outgoing.length },
    { id: "incoming", label: "Incoming", n: incoming.length },
    { id: "returning", label: "Returning", n: returning.length },
  ];
  const shown = tab === "outgoing" ? outgoing : tab === "incoming" ? incoming : returning;

  return (
    <div className={"city-moves side-card" + (open ? "" : " collapsed")}>
      <div className="cm-head">
        <button className="cm-head-btn" onClick={() => setOpen(o => !o)}>
          <span>🪖 Movements{activeCount > 0 ? ` · ${activeCount}` : ""}</span>
          <span className={"cm-chevron" + (incoming.length ? " threat" : "")}>{open ? "▾" : "▸"}</span>
        </button>
        {onExpand && <button className="cm-expand" title="Empire overview" onClick={onExpand}>⤢</button>}
      </div>
      {open && (
        <>
          <div className="cm-tabs">
            {tabs.map(t => (
              <button key={t.id} className={"cm-tab" + (tab === t.id ? " active" : "") + (t.id === "incoming" && t.n > 0 ? " threat" : "")}
                onClick={() => setTab(t.id)}>{t.label}{t.n > 0 ? ` (${t.n})` : ""}</button>
            ))}
          </div>
          <div className="cm-list">
            {shown.length === 0
              ? <EmptyMovements />
              : shown.map(m => <MovementRow key={m.id} m={m} now={now} />)}
          </div>
          {reinf.length > 0 && (
            <div className="cm-reinf">
              <div className="cm-reinf-head">🤝 Stationed support</div>
              {reinf.map((r, i) => (
                <div key={i} className="cm-reinf-row">
                  <span className="mv-label">{r.mine ? "Your troops" : r.owner}</span>
                  <span className="mv-troops">{troopSummary(r.units).map(t => <span key={t.type} title={t.type}>{t.glyph}{t.n}</span>)}</span>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

function MovementRow({ m, now }: { m: Movement; now: number }) {
  const meta = kindMeta(m);

  // trade convoy: cargo lives in `loot`; PENDING convoys haven't departed yet
  if (m.type === "TRADE") {
    const cargo = m.loot ? Object.entries(m.loot).filter(([, v]) => v > 0) : [];
    const pending = m.status === "PENDING";
    const pct = pending ? 0 : progressPct(m.departAt, m.arriveAt, now);
    return (
      <div className={"mv-row " + meta.cls}>
        <div className="mv-top">
          <span className="mv-icon">{meta.icon}</span>
          <span className="mv-route">{m.originCity} → <b>{m.targetCity}</b></span>
          <span className="mv-eta">{pending ? "queued" : fmtEta(m.arriveAt, now)}</span>
        </div>
        <div className="mv-sub">
          <span className="mv-label">{pending ? "Waiting for a convoy slot" : meta.label}</span>
          <span className="mv-troops">{cargo.map(([k, v]) => <span key={k} title={titleCase(k)}>{RES_GLYPH[k] ?? "📦"}{Math.round(v)}</span>)}</span>
        </div>
        {!pending && <div className="mv-bar"><i className={meta.cls} style={{ width: pct + "%" }} /></div>}
      </div>
    );
  }

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
