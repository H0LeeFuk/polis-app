import { useEffect, useState } from "react";
import { getLibrary, startLibraryResearch, respecLibrary } from "../api";
import type { LibraryData, LibraryNode } from "../types";

const BRANCHES: { key: "WAR" | "WARDS" | "LORE"; icon: string; title: string }[] = [
  { key: "WAR", icon: "⚔", title: "War" },
  { key: "WARDS", icon: "🛡", title: "Wards" },
  { key: "LORE", icon: "📜", title: "Lore & Dominion" },
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
  const [, force] = useState(0);
  useEffect(() => { const t = setInterval(() => force(x => x + 1), 1000); return () => clearInterval(t); }, []);

  const load = () => getLibrary(cityId).then(setData).catch(e => setErr(e.message));
  useEffect(() => { load(); }, [cityId]);

  const research = async (n: LibraryNode) => {
    setErr("");
    try { await startLibraryResearch(cityId, n.id); await load(); onChanged?.(); } catch (e: any) { setErr(e.message); }
  };
  const respec = async () => {
    if (!confirm("Reset ALL research for this city? Refunds every point. Costs resources.")) return;
    setErr("");
    try { await respecLibrary(cityId); await load(); onChanged?.(); } catch (e: any) { setErr(e.message); }
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
              <span className="muted">Full tree costs {data.fullTreeCost} — specialize.</span>
              {data.raceAffinity && <span className="lib-affinity">{data.race} city · {data.raceAffinity}</span>}
            </div>
            {err && <div className="hero-inline-err">{err}</div>}
            <div className="lib-branches">
              {BRANCHES.map(b => (
                <div className="lib-branch" key={b.key}>
                  <h3>{b.icon} {b.title}</h3>
                  {[1, 2, 3].map(tier => (
                    <div className="lib-tier" key={tier}>
                      {data.tree.filter(n => n.branch === b.key && n.tier === tier).map(n => (
                        <LibNode key={n.id} n={n} onResearch={() => research(n)} />
                      ))}
                    </div>
                  ))}
                </div>
              ))}
            </div>
            <div className="lib-foot">
              <button className="btn ghost danger" onClick={respec}>↺ Reset research (re-spec)</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function LibNode({ n, onResearch }: { n: LibraryNode; onResearch: () => void }) {
  const cls = "lib-node st-" + n.state.toLowerCase() + (n.id === "dominion" ? " marquee" : "") + (n.available ? " avail" : "");
  return (
    <div className={cls}>
      <div className="lib-node-head"><b>{n.name}</b><span className="lib-cost">{n.pointCost}◈</span></div>
      <div className="muted lib-effect">{n.effect}</div>
      {n.state === "COMPLETED" ? <div className="lib-done">✓ Researched</div>
        : n.state === "RESEARCHING" ? <div className="lib-researching">⏳ {countdown(n.completesAt)}</div>
          : n.available ? <button className="btn" onClick={onResearch}>Research · {fmtDur(n.durationSeconds)}</button>
            : <div className="muted lib-locked">🔒 {n.minLibraryLevel > 0 ? `Library ${n.minLibraryLevel}` : "prereq"} </div>}
    </div>
  );
}
