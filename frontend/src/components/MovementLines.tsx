import { useEffect, useRef, useState } from "react";
import type { Movement, WorldIsland } from "../types";
import { type MapLinePrefs, colorFor, INCOMING_COLOR } from "../mapMovementPrefs";
import { ISLAND_SIZE } from "./WorldView";

const SLOTS_PER_ISLAND = 12;
const TYPE_ICON: Record<string, string> = {
  ATTACK: "⚔", RETURN: "↩", SUPPORT: "🛡", TRADE: "🔁", OCCUPY: "⛏",
  COLONY: "🚢", SETTLE: "🏛", SPY: "🕵",
};
const fmtEta = (s: number) => {
  if (s <= 0) return "arriving";
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  return h > 0 ? `${h}h ${m}m` : m > 0 ? `${m}m ${sec}s` : `${sec}s`;
};

/** Map-space position of a city marker — mirrors WorldView's island/slot geometry exactly. */
function cityPos(islands: WorldIsland[], cityId: number | null, padX: number, padY: number): { x: number; y: number } | null {
  if (cityId == null) return null;
  for (const isl of islands) {
    const c = isl.cities.find(x => x.id === cityId);
    if (!c) continue;
    const size = ISLAND_SIZE;   // constant island size — must match WorldView's render
    const a = (c.slot / SLOTS_PER_ISLAND) * 2 * Math.PI - Math.PI / 2;
    const rad = size * 0.33;
    // .island is centred on (px+PAD, py+PAD) via translate(-50%,-50%); the city dot sits at
    // island-centre + (cos,sin)*rad — so NO +size/2 offset (that was anchoring to the top-left).
    return { x: isl.px + padX + Math.cos(a) * rad, y: isl.py + padY + Math.sin(a) * rad };
  }
  return null;
}

/**
 * Live movement overlay for the world map. One dashed line per movement (the player's own + incoming
 * attacks) with a marker sliding origin→destination, interpolated client-side from departAt/arriveAt.
 * Lines are semi-transparent at rest and sharpen on hover/click; incoming attacks pulse. Lives in
 * map-space (inside the scaled .wspace) so it pans/zooms with the map.
 */
export default function MovementLines({ movements, islands, prefs, space, padX, padY, onOpenCity }: {
  movements: Movement[]; islands: WorldIsland[]; prefs: MapLinePrefs;
  space: number; padX: number; padY: number; onOpenCity?: (cityId: number) => void;
}) {
  const [nowMs, setNowMs] = useState(Date.now());
  const [activeId, setActiveId] = useState<number | null>(null);
  const raf = useRef<number | null>(null);
  // glide the markers ~10fps (motion is minutes-long, so this is smooth and light)
  useEffect(() => {
    let alive = true;
    const tick = () => { if (!alive) return; setNowMs(Date.now()); raf.current = window.setTimeout(tick, 250) as unknown as number; };
    tick();
    return () => { alive = false; if (raf.current) clearTimeout(raf.current); };
  }, []);

  if (!prefs.enabled) return null;

  // resolve drawable movements (both endpoints map to a city position, and still in flight)
  const drawn = movements.map(m => {
    const o = cityPos(islands, m.originCityId, padX, padY);
    const t = cityPos(islands, m.targetCityId, padX, padY);
    if (!o || !t) return null;
    const dep = new Date(m.departAt).getTime(), arr = new Date(m.arriveAt).getTime();
    const dur = Math.max(1, arr - dep);
    const progress = Math.max(0, Math.min(1, (nowMs - dep) / dur));
    if (nowMs >= arr) return null;                       // finished — drops off (next poll removes it)
    const incoming = m.hostile === true;
    return {
      m, o, t, incoming,
      pos: { x: o.x + (t.x - o.x) * progress, y: o.y + (t.y - o.y) * progress },
      angle: Math.atan2(t.y - o.y, t.x - o.x) * 180 / Math.PI,
      etaSec: Math.round((arr - nowMs) / 1000),
      color: colorFor(m.type, incoming, prefs),
    };
  }).filter(Boolean) as Array<{ m: Movement; o: {x:number;y:number}; t:{x:number;y:number}; incoming:boolean; pos:{x:number;y:number}; angle:number; etaSec:number; color:string }>;

  const active = drawn.find(d => d.m.id === activeId) || null;

  return (
    <>
      <svg className="mv-overlay" width={space} height={space} viewBox={`0 0 ${space} ${space}`}
        style={{ position: "absolute", left: 0, top: 0, pointerEvents: "none", overflow: "visible" }}>
        {drawn.map(d => {
          const hot = activeId === d.m.id;
          const op = hot ? 0.95 : (activeId != null ? Math.min(prefs.opacity, 0.18) : prefs.opacity);
          return (
            <g key={d.m.id} opacity={op}>
              <line x1={d.o.x} y1={d.o.y} x2={d.t.x} y2={d.t.y}
                stroke={d.color} strokeWidth={hot ? 3 : 1.6} strokeDasharray="10 8"
                className={d.incoming ? "mv-line mv-incoming" : "mv-line"} />
              {/* fat invisible hit-line so the thin dash is easy to click/hover */}
              <line x1={d.o.x} y1={d.o.y} x2={d.t.x} y2={d.t.y} stroke="transparent" strokeWidth={16}
                style={{ pointerEvents: "stroke", cursor: "pointer" }}
                onMouseEnter={() => setActiveId(d.m.id)} onMouseLeave={() => setActiveId(a => a === d.m.id ? null : a)}
                onClick={() => setActiveId(d.m.id)} />
              {/* sliding marker: a direction wedge + a type glyph */}
              <g transform={`translate(${d.pos.x} ${d.pos.y})`} style={{ pointerEvents: "auto", cursor: "pointer" }}
                onMouseEnter={() => setActiveId(d.m.id)} onClick={() => setActiveId(d.m.id)}>
                {d.incoming && <circle r={15} className="mv-pulse" fill={INCOMING_COLOR} />}
                <g transform={`rotate(${d.angle})`}>
                  <polygon points="9,0 -6,6 -6,-6" fill={d.color} stroke="#1b1410" strokeWidth={1} />
                </g>
                <circle r={9} fill="rgba(20,14,10,0.85)" stroke={d.color} strokeWidth={1.5} />
                <text x={0} y={3.5} textAnchor="middle" fontSize={10}>{TYPE_ICON[d.m.type] ?? "•"}</text>
              </g>
            </g>
          );
        })}
      </svg>

      {active && (
        <div className={"mv-popover" + (active.incoming ? " incoming" : "")}
          style={{ position: "absolute", left: active.pos.x + 14, top: active.pos.y - 10 }}
          onClick={e => e.stopPropagation()}>
          <button className="mv-pop-close" onClick={() => setActiveId(null)}>✕</button>
          <div className="mv-pop-title">{TYPE_ICON[active.m.type] ?? "•"} {active.incoming ? "Incoming attack" : titleCase(active.m.type)}</div>
          <div className="mv-pop-route">{active.m.originCity || "?"} → {active.m.targetCity || "?"}</div>
          <div className="mv-pop-eta">Arrives in {fmtEta(active.etaSec)}</div>
          <div className="mv-pop-troops">
            {active.m.unitsKnown && active.m.units
              ? Object.entries(active.m.units).filter(([, n]) => n > 0).map(([u, n]) => `${n}× ${titleCase(u)}`).join(", ") || "—"
              : <span className="muted">Unknown forces</span>}
          </div>
          {active.m.targetCityId != null && onOpenCity && (
            <button className="btn ghost tiny" onClick={() => onOpenCity(active.m.targetCityId!)}>Open city</button>
          )}
        </div>
      )}
    </>
  );
}

const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
