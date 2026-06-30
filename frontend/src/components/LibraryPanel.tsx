import { useEffect, useState } from "react";
import { getLibrary, startLibraryResearch, respecLibrary, callCityGuard } from "../api";
import type { LibraryData, LibraryNode } from "../types";

const BRANCHES: { key: "WAR" | "WARDS" | "LORE"; icon: string; title: string }[] = [
  { key: "WAR", icon: "⚔", title: "The Warpath" },
  { key: "WARDS", icon: "🛡", title: "The Bastion" },
  { key: "LORE", icon: "🐺", title: "The Wild Hunt" },
];

const fmtDur = (s: number) => {
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
};
function countdown(iso?: string): string {
  if (!iso) return "";
  let s = Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
  const h = Math.floor(s / 3600); s -= h * 3600; const m = Math.floor(s / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m ${s - m * 60}s`;
}

export default function LibraryPanel({ cityId, onClose, onChanged }: {
  cityId: number; onClose: () => void; onChanged?: () => void;
}) {
  const [data, setData] = useState<LibraryData | null>(null);
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  const [, force] = useState(0);
  useEffect(() => { const t = setInterval(() => force(x => x + 1), 1000); return () => clearInterval(t); }, []);

  const load = () => getLibrary(cityId).then(d => { setData(d); setErr(""); }).catch(e => setErr(e.message));
  useEffect(() => { load(); }, [cityId]);

  const research = async (n: LibraryNode) => {
    setErr(""); setBusy(true);
    try { await startLibraryResearch(cityId, n.id); await load(); onChanged?.(); }
    catch (e: any) { setErr(e.message); }
    finally { setBusy(false); }
  };
  const respec = async () => {
    if (!confirm("Reset ALL research for this city? Refunds every point. Costs resources.")) return;
    setErr(""); setBusy(true);
    try { await respecLibrary(cityId); await load(); onChanged?.(); }
    catch (e: any) { setErr(e.message); }
    finally { setBusy(false); }
  };
  const callGuard = async () => {
    setErr(""); setBusy(true);
    try { const r = await callCityGuard(cityId); setErr(`Summoned ${r.summoned} militia to the garrison.`); onChanged?.(); }
    catch (e: any) { setErr(e.message); }
    finally { setBusy(false); }
  };
  const guardReady = !!data?.tree.find(n => n.id === "city_guard" && n.state === "COMPLETED");

  // Why is a locked node not yet researchable? Spell it out so the tree is self-explanatory.
  const lockReason = (n: LibraryNode): string => {
    if (!data) return "";
    if (data.level < n.minLibraryLevel) return `Needs Library level ${n.minLibraryLevel}`;
    if (!n.tierOk) return `Needs any Tier ${n.tier - 1} research in this branch`;
    if (data.tree.some(x => x.state === "RESEARCHING")) return "Another research in progress";
    if (data.availablePoints < n.pointCost) return `Needs ${n.pointCost - data.availablePoints} more point(s)`;
    return "Locked";
  };

  return (
    <div className="mvov-backdrop" onClick={onClose}>
      <div className="mvov library-panel" onClick={e => e.stopPropagation()}>
        <div className="mvov-head">
          <h2>📚 Library{data ? ` — Level ${data.level} / ${data.maxLevel}` : ""}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        {!data ? <p className="muted" style={{ padding: 24 }}>Opening the tomes…</p> : (
          <div className="library-body">
            <div className="lib-summary">
              <span className="lib-points">Available <b>{data.availablePoints}</b> · Spent {data.spentPoints} · Total {data.totalPoints}</span>
              <span className="muted">Full tree costs {data.fullTreeCost} pts — you can master ~2 branches, so specialize.</span>
              {data.raceAffinity && <span className="lib-affinity">{data.race} · {data.raceAffinity}</span>}
            </div>
            {err && <div className="hero-inline-err">{err}</div>}

            <div className="lib-tree">
              {BRANCHES.map(b => (
                <div className="lib-branch" key={b.key}>
                  <h3 className={"lib-branch-head br-" + b.key.toLowerCase()}>{b.icon} {b.title}</h3>
                  {[1, 2, 3].map(tier => {
                    const nodes = data.tree.filter(n => n.branch === b.key && n.tier === tier);
                    if (!nodes.length) return null;
                    return (
                      <div className="lib-tier-wrap" key={tier}>
                        {tier > 1 && <div className="lib-connector" aria-hidden>↓</div>}
                        <div className="lib-tier-label">Tier {tier}</div>
                        <div className="lib-tier">
                          {nodes.map(n => (
                            <LibNode key={n.id} n={n} busy={busy}
                              reason={lockReason(n)} onResearch={() => research(n)} />
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>

            <div className="lib-foot">
              <span className="muted lib-legend">
                <i className="lg done" /> researched
                <i className="lg busy" /> in progress
                <i className="lg avail" /> ready
                <i className="lg locked" /> locked
              </span>
              {guardReady && <button className="btn" onClick={callGuard} disabled={busy}>🧑‍🌾 Call the Guard</button>}
              <button className="btn ghost danger" onClick={respec} disabled={busy}>↺ Reset research (re-spec)</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function LibNode({ n, reason, busy, onResearch }: {
  n: LibraryNode; reason: string; busy: boolean; onResearch: () => void;
}) {
  const stateCls = n.state === "COMPLETED" ? "done"
    : n.state === "RESEARCHING" ? "busy"
      : n.available ? "avail" : "locked";
  const cls = "lib-node st-" + stateCls + (n.id === "conquest" ? " marquee" : "");
  return (
    <div className={cls}>
      <div className="lib-node-head"><b>{n.name}</b><span className="lib-cost">{n.pointCost}◈</span></div>
      <div className="muted lib-effect">{n.effect}</div>

      {n.state === "COMPLETED" ? <div className="lib-done">✓ Researched</div>
        : n.state === "RESEARCHING" ? <div className="lib-researching">⏳ {countdown(n.completesAt)}</div>
          : n.available ? <button className="btn" disabled={busy} onClick={onResearch}>Research · {fmtDur(n.durationSeconds)}</button>
            : <div className="muted lib-locked">🔒 {reason}</div>}
    </div>
  );
}
