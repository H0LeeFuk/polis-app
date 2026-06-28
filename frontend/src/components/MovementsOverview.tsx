import { useEffect, useMemo, useState } from "react";
import { getWorld } from "../api";
import type { Movement, PlayerMovements, WorldData } from "../types";
import { fmtEta, fmtArrival, progressPct, troopSummary, kindMeta, moveKind } from "../movements";

type Filter = "all" | "attack" | "return" | "incoming" | "colony" | "support";
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

/** Area 3 — full-screen bird's-eye view of every troop movement across the empire. */
export default function MovementsOverview({ data, now, onClose, onGoCity }: {
  data: PlayerMovements | null; now: number; onClose: () => void; onGoCity: (cityId: number | null) => void;
}) {
  const [filter, setFilter] = useState<Filter>("all");
  const [cityFilter, setCityFilter] = useState<string>("");
  const [world, setWorld] = useState<WorldData | null>(null);

  useEffect(() => { getWorld().then(setWorld).catch(() => {}); }, []);

  const all = data?.movements ?? [];
  const s = data?.summary;

  // cities referenced by any movement, for the city filter dropdown
  const cityOptions = useMemo(() => {
    const m = new Map<string, string>();
    for (const mv of all) {
      if (mv.originCity) m.set(mv.originCity, mv.originCity);
      if (mv.targetCity) m.set(mv.targetCity, mv.targetCity);
    }
    return [...m.keys()].sort();
  }, [all]);

  const rows = all.filter(mv => {
    if (filter !== "all" && moveKind(mv) !== filter) return false;
    if (cityFilter && mv.originCity !== cityFilter && mv.targetCity !== cityFilter) return false;
    return true;
  }).sort((a, b) => new Date(a.arriveAt).getTime() - new Date(b.arriveAt).getTime());

  const filters: { id: Filter; label: string }[] = [
    { id: "all", label: "All" },
    { id: "attack", label: "⚔ Attacks" },
    { id: "return", label: "↩ Returning" },
    { id: "incoming", label: "⚠ Incoming" },
    { id: "colony", label: "🚢 Colony" },
    { id: "support", label: "🤝 Support" },
  ];

  return (
    <div className="mvov-backdrop" onClick={onClose}>
      <div className="mvov" onClick={e => e.stopPropagation()}>
        <div className="mvov-head">
          <h2>🪖 Troop Movements</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        {/* summary bar */}
        <div className="mvov-summary">
          <div className="mvov-stat"><span>⚔ Attacks out</span><b>{s?.attacksOut ?? 0}</b></div>
          <div className={"mvov-stat" + ((s?.incomingThreats ?? 0) > 0 ? " threat" : "")}><span>🛡 Incoming threats</span><b>{s?.incomingThreats ?? 0}</b></div>
          <div className="mvov-stat"><span>🔄 Returning</span><b>{s?.returning ?? 0}</b></div>
          <div className="mvov-stat"><span>✅ Idle cities</span><b>{s?.idleCities ?? 0}</b></div>
        </div>

        <MiniMap world={world} moves={all} now={now} />

        {/* filters */}
        <div className="mvov-filters">
          {filters.map(f => (
            <button key={f.id} className={"mvov-fbtn" + (filter === f.id ? " active" : "")} onClick={() => setFilter(f.id)}>{f.label}</button>
          ))}
          <select className="mvov-cityfilter" value={cityFilter} onChange={e => setCityFilter(e.target.value)}>
            <option value="">All cities</option>
            {cityOptions.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
        </div>

        {/* table / card list */}
        <div className="mvov-table">
          <div className="mvov-trow mvov-thead">
            <span>From</span><span>To</span><span>Type</span><span>Troops</span><span>Status</span><span>ETA</span><span>Progress</span>
          </div>
          {rows.length === 0 ? (
            <div className="mvov-empty muted">No movements match — the seas are calm.</div>
          ) : rows.map(m => <OverviewRow key={m.id} m={m} now={now} onGoCity={onGoCity} />)}
        </div>
      </div>
    </div>
  );
}

function OverviewRow({ m, now, onGoCity }: { m: Movement; now: number; onGoCity: (id: number | null) => void; }) {
  const meta = kindMeta(m);
  const pct = progressPct(m.departAt, m.arriveAt, now);
  const troops = troopSummary(m.units);
  // clicking opens the most relevant of the player's own cities
  const goto = m.type === "RETURN" ? m.targetCityId : (m.hostile ? m.targetCityId : m.originCityId);
  return (
    <div className={"mvov-trow " + meta.cls} onClick={() => onGoCity(goto)}>
      <span data-l="From">{m.originCity}{m.hostile && <small className="muted"> ({m.owner})</small>}</span>
      <span data-l="To">{m.targetCity}</span>
      <span data-l="Type"><span className={"mvov-badge " + meta.cls}>{meta.icon} {m.type}</span></span>
      <span data-l="Troops">
        {m.unitsKnown
          ? (troops.length ? troops.map(t => <span key={t.type} className="mvov-troop" title={t.type}>{t.glyph}{t.n}</span>) : "—")
          : <span className="muted">Unknown</span>}
      </span>
      <span data-l="Status">{m.status}</span>
      <span data-l="ETA"><b>{fmtEta(m.arriveAt, now)}</b><small className="muted"> · {fmtArrival(m.arriveAt)}</small></span>
      <span data-l="Progress"><span className="mvov-bar"><i className={meta.cls} style={{ width: pct + "%" }} /></span></span>
    </div>
  );
}

// ---- mini map: animated dots interpolated from departAt → arriveAt ----

function MiniMap({ world, moves, now }: { world: WorldData | null; moves: Movement[]; now: number; }) {
  // map every cityId → world-space pixel coordinate, mirroring WorldView's layout maths
  const coords = useMemo(() => {
    const map = new Map<number, { x: number; y: number }>();
    if (!world) return map;
    for (const isl of world.islands) {
      const size = 130 + isl.cities.length * 16;
      const rad = size * 0.33;
      for (const c of isl.cities) {
        const a = (c.slot / 10) * 2 * Math.PI - Math.PI / 2;
        map.set(c.id, { x: isl.px + Math.cos(a) * rad, y: isl.py + Math.sin(a) * rad });
      }
    }
    return map;
  }, [world]);

  if (!world) return <div className="mvov-map loading muted">Charting the seas…</div>;

  const dots = moves.map(m => {
    const from = m.originCityId != null ? coords.get(m.originCityId) : undefined;
    const to = m.targetCityId != null ? coords.get(m.targetCityId) : undefined;
    if (!from || !to) return null;
    const p = progressPct(m.departAt, m.arriveAt, now) / 100;
    return { id: m.id, kind: moveKind(m), x: from.x + (to.x - from.x) * p, y: from.y + (to.y - from.y) * p, from, to };
  }).filter(Boolean) as { id: number; kind: string; x: number; y: number; from: { x: number; y: number }; to: { x: number; y: number } }[];

  const colour: Record<string, string> = {
    attack: "#e0524a", incoming: "#ff3b30", return: "#4aa3df", colony: "#d8ad53", support: "#6fd08a",
  };

  return (
    <svg className="mvov-map" viewBox="0 0 1500 1100" preserveAspectRatio="xMidYMid meet">
      <rect x="0" y="0" width="1500" height="1100" fill="#0d4f86" />
      {world.islands.map(isl => (
        <g key={isl.id}>
          <circle cx={isl.px} cy={isl.py} r={28 + isl.cities.length * 3} fill="#5d9a30" stroke="#e9d7a6" strokeWidth="4" />
        </g>
      ))}
      {dots.map(d => (
        <g key={d.id}>
          <line x1={d.from.x} y1={d.from.y} x2={d.to.x} y2={d.to.y} stroke={colour[d.kind]} strokeWidth="2" strokeDasharray="6 6" opacity="0.5" />
          <circle cx={d.x} cy={d.y} r="11" fill={colour[d.kind]} stroke="#fff" strokeWidth="3">
            {d.kind === "incoming" && <animate attributeName="r" values="11;15;11" dur="1s" repeatCount="indefinite" />}
          </circle>
        </g>
      ))}
    </svg>
  );
}
