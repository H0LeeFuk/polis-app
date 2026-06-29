import { useEffect, useState } from "react";
import { getWatchtower, getSpyReports, getSpyAlerts, doBuild } from "../api";
import type { WatchtowerDto, SpyReportDto, SpyAlertDto } from "../types";

const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const fmt = (n: number) => n >= 1000 ? (n / 1000).toFixed(1) + "k" : Math.floor(n).toString();
function ago(iso: string): string {
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (s < 60) return s + "s ago";
  const m = Math.floor(s / 60); if (m < 60) return m + "m ago";
  const h = Math.floor(m / 60); return h + "h ago";
}

/**
 * Watchtower + espionage hub: shows this city's spy/defence chances, lets you upgrade the tower, and
 * lists intel gathered (Spy Reports) plus alerts about enemy spies your tower caught.
 */
export default function SpyPanel({ cityId, onClose, onChanged }: {
  cityId: number; onClose: () => void; onChanged: () => void;
}) {
  const [wt, setWt] = useState<WatchtowerDto | null>(null);
  const [reports, setReports] = useState<SpyReportDto[]>([]);
  const [alerts, setAlerts] = useState<SpyAlertDto[]>([]);
  const [open, setOpen] = useState<number | null>(null);
  const [err, setErr] = useState("");

  const load = () => {
    getWatchtower(cityId).then(setWt).catch((e: any) => setErr(e.message));
    getSpyReports().then(setReports).catch(() => {});
    getSpyAlerts().then(setAlerts).catch(() => {});
  };
  useEffect(load, [cityId]);

  const upgrade = async () => {
    setErr("");
    try { await doBuild(cityId, "WATCHTOWER"); onChanged(); load(); }
    catch (e: any) { setErr(e.message); }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(600px,100%)" }}>
        <div className="modal-header">
          <h2>🕵 Watchtower & Espionage</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          {wt && (
            <div className="watchtower-box">
              <div className="wt-head">
                <h3>Watchtower · Level {wt.level}{wt.level >= wt.maxLevel ? " (max)" : ""}</h3>
                {wt.level < wt.maxLevel && <button className="btn" onClick={upgrade}>⬆ Upgrade</button>}
              </div>
              <div className="wt-chances">
                <div><span className="wt-ico">🎯</span><b>{wt.spySuccessChance}%</b><small>spy success</small></div>
                <div><span className="wt-ico">🛡</span><b>{wt.spyDefenseChance}%</b><small>catch enemy spies</small></div>
              </div>
              <p className="muted" style={{ fontSize: 12 }}>
                Spy enemy cities before attacking. A higher Watchtower is more likely to spy successfully and
                to catch enemy spies. A spy costs {fmt(wt.cost)} of each resource and resolves in {Math.round(wt.seconds / 60) || 1}m.
              </p>
            </div>
          )}
          {err && <div className="hero-inline-err">{err}</div>}

          {alerts.length > 0 && (
            <>
              <div className="br-section-label">⚠ Spies caught (you were scouted)</div>
              {alerts.map(a => (
                <div className="spy-alert" key={a.id}>
                  Enemy spy from <b>{a.spyingPlayerName}</b> caught scouting <b>{a.targetCityName}</b> · <span className="muted">{ago(a.caughtAt)}</span>
                </div>
              ))}
            </>
          )}

          <div className="br-section-label">Spy reports (intel gathered)</div>
          {reports.length === 0 && <p className="muted">No spy reports yet. Spy an enemy city from the world map.</p>}
          {reports.map(r => (
            <div className={"spy-report " + (r.outcome === "SUCCESS" ? "ok" : "caught")} key={r.id}>
              <div className="spy-report-head" onClick={() => setOpen(open === r.id ? null : r.id)}>
                <span>{r.outcome === "SUCCESS" ? "✅" : "🚨"} {r.targetCityName}</span>
                <span className="muted">{r.outcome === "SUCCESS" ? "Intel" : "Caught"} · {ago(r.capturedAt)}</span>
              </div>
              {open === r.id && r.outcome === "SUCCESS" && (
                <div className="spy-report-body">
                  <p className="muted" style={{ fontSize: 11 }}>Intel from {ago(r.capturedAt)} — defenses may have changed.</p>
                  <div className="spy-grid">
                    <div>
                      <h4>Troops</h4>
                      {r.troops && Object.keys(r.troops).length
                        ? Object.entries(r.troops).map(([t, n]) => <div key={t}>{titleCase(t)} <b>×{n}</b></div>)
                        : <span className="muted">None garrisoned</span>}
                    </div>
                    <div>
                      <h4>Resources</h4>
                      {r.resources && Object.entries(r.resources).map(([t, n]) => <div key={t}>{titleCase(t)} <b>{fmt(n)}</b></div>)}
                    </div>
                    <div>
                      <h4>Buildings</h4>
                      {r.buildings && Object.entries(r.buildings).filter(([, l]) => l > 0).length
                        ? Object.entries(r.buildings).filter(([, l]) => l > 0)
                            .map(([t, l]) => <div key={t}>{titleCase(t)} <b>Lv {l}</b></div>)
                        : <span className="muted">Undeveloped</span>}
                    </div>
                  </div>
                </div>
              )}
              {open === r.id && r.outcome === "CAUGHT" && (
                <div className="spy-report-body"><p className="muted">Your spy was caught — no intel, and the target now knows it was you.</p></div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
