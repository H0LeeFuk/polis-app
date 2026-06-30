import { useEffect, useState } from "react";
import {
  getTroopsAbroad, getForeignTroops, recallAbroad, dismissForeign,
  type TroopsAbroadRow, type ForeignTroopsRow,
} from "../api";
import { troopSummary } from "../movements";

const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const sumTroops = (t: Record<string, number>) => Object.values(t).reduce((a, b) => a + (b > 0 ? b : 0), 0);

const LOC_BADGE: Record<string, { icon: string; label: string }> = {
  ALLY_CITY: { icon: "🤝", label: "ally city" },
  NODE: { icon: "⛏", label: "node" },
  SIEGE: { icon: "⚑", label: "siege" },
};

/** Icons + counts for a unit map. */
function TroopIcons({ troops }: { troops: Record<string, number> }) {
  const list = troopSummary(troops);
  if (!list.length) return <span className="muted">—</span>;
  return <span className="td-troops">{list.map(t => <span key={t.type} title={titleCase(t.type)}>{t.glyph}{t.n}</span>)}</span>;
}

/**
 * Two-tab overview of troops away from this city and foreign troops garrisoned in it.
 * Recall pulls your away troops home; Dismiss ejects foreign troops back to their owner.
 */
export default function TroopDetailPanel({ cityId, cityName, onClose, onChanged }: {
  cityId: number; cityName: string; onClose: () => void; onChanged?: () => void;
}) {
  const [tab, setTab] = useState<"abroad" | "foreign">("abroad");
  const [abroad, setAbroad] = useState<TroopsAbroadRow[] | null>(null);
  const [foreign, setForeign] = useState<ForeignTroopsRow[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  const load = () => {
    getTroopsAbroad(cityId).then(setAbroad).catch(e => setErr(e.message));
    getForeignTroops(cityId).then(setForeign).catch(e => setErr(e.message));
  };
  useEffect(load, [cityId]);

  const act = async (fn: () => Promise<unknown>) => {
    setErr(""); setBusy(true);
    try { await fn(); load(); onChanged?.(); }
    catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };

  const recall = (row: TroopsAbroadRow) => {
    const warn = row.locationType === "NODE"
      ? "Recall these troops? If it empties the node's garrison, you will abandon the node."
      : row.locationType === "SIEGE"
        ? "Recall the besieging force? This withdraws your troops and may break the siege."
        : `Recall your troops from ${row.locationName}? They march back to ${cityName}.`;
    if (!window.confirm(warn)) return;
    act(() => recallAbroad(cityId, row.locationType, row.locationId));
  };
  const dismiss = (row: ForeignTroopsRow) => {
    if (!window.confirm(`Send ${row.ownerName}'s troops home? They leave ${cityName} and return to their owner.`)) return;
    act(() => dismissForeign(cityId, row.ownerPlayerId));
  };

  const abroadLocs = abroad?.length ?? 0;
  const abroadTroops = (abroad ?? []).reduce((a, r) => a + sumTroops(r.troops), 0);
  const foreignAllies = foreign?.length ?? 0;
  const foreignTroopsN = (foreign ?? []).reduce((a, r) => a + sumTroops(r.troops), 0);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window td-window" onClick={e => e.stopPropagation()} style={{ width: "min(620px,100%)" }}>
        <div className="modal-header">
          <h2>🪖 Troop Detail — {cityName}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="td-tabs">
            <button className={"td-tab" + (tab === "abroad" ? " active" : "")} onClick={() => setTab("abroad")}>
              My Troops Abroad{abroadLocs ? ` (${abroadLocs})` : ""}
            </button>
            <button className={"td-tab" + (tab === "foreign" ? " active" : "")} onClick={() => setTab("foreign")}>
              Foreign Troops Here{foreignAllies ? ` (${foreignAllies})` : ""}
            </button>
          </div>
          {err && <div className="hero-inline-err">{err}</div>}

          {tab === "abroad" ? (
            <>
              <div className="td-summary muted">Abroad: {abroadLocs} location{abroadLocs === 1 ? "" : "s"} · {abroadTroops.toLocaleString()} troops</div>
              {abroad == null ? <p className="muted">Loading…</p>
                : abroad.length === 0 ? <p className="muted td-empty">No troops stationed abroad.</p>
                  : abroad.map(row => {
                    const badge = LOC_BADGE[row.locationType];
                    return (
                      <div className="td-row" key={row.locationType + row.locationId}>
                        <span className="td-loc"><span className="td-badge" title={badge.label}>{badge.icon}</span> {row.locationName} <small className="muted">({badge.label})</small></span>
                        <TroopIcons troops={row.troops} />
                        <button className="btn ghost tiny" disabled={busy} onClick={() => recall(row)}>↩ Recall</button>
                      </div>
                    );
                  })}
            </>
          ) : (
            <>
              <div className="td-summary muted">Foreign here: {foreignAllies} {foreignAllies === 1 ? "ally" : "allies"} · {foreignTroopsN.toLocaleString()} troops</div>
              <p className="muted td-note">You can dismiss these; the owner can also recall them from their side.</p>
              {foreign == null ? <p className="muted">Loading…</p>
                : foreign.length === 0 ? <p className="muted td-empty">No foreign troops in this city.</p>
                  : foreign.map(row => (
                    <div className="td-row" key={row.ownerPlayerId}>
                      <span className="td-loc"><span className="td-badge" title="player">🛡</span> {row.ownerName}{row.ownerAlliance ? <small className="muted"> [{row.ownerAlliance}]</small> : ""}</span>
                      <TroopIcons troops={row.troops} />
                      <button className="btn ghost tiny" disabled={busy} onClick={() => dismiss(row)}>✕ Dismiss</button>
                    </div>
                  ))}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
