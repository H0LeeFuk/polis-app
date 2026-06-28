import { useEffect, useState } from "react";
import { getRankings, createAlliance } from "../api";
import type { RankRow } from "../types";

const TABS: [string, string][] = [
  ["points", "Players · points"], ["combat", "Players · combat"],
  ["alliancePoints", "Alliances · points"], ["alliances", "Alliances · members"],
];

export default function Rankings() {
  const [tab, setTab] = useState("points");
  const [rows, setRows] = useState<RankRow[]>([]);
  const [reload, setReload] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [tag, setTag] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  useEffect(() => { getRankings(tab).then(setRows).catch(() => setRows([])); }, [tab, reload]);

  async function submit() {
    setErr(""); setBusy(true);
    try {
      await createAlliance(tag.trim(), name.trim());
      setShowCreate(false); setTag(""); setName("");
      setTab("alliancePoints"); setReload(r => r + 1);
    } catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  }

  const onAllianceTab = tab === "alliancePoints" || tab === "alliances";
  return (
    <div className="panel">
      <div className="tabs">
        {TABS.map(([k, label]) => (
          <button key={k} className={"tab" + (tab === k ? " active" : "")} onClick={() => setTab(k)}>{label}</button>
        ))}
      </div>
      {onAllianceTab && (
        <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>
          <button className="btn" style={{ width: "auto", padding: "7px 14px" }}
            onClick={() => { setErr(""); setShowCreate(true); }}>
            ➕ Create alliance
          </button>
        </div>
      )}
      <table>
        <thead><tr><th>#</th><th>Name</th><th>Score</th><th></th></tr></thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}><td>{i + 1}</td><td>{r.name}</td><td>{r.value.toLocaleString()}</td><td className="muted">{r.sub}</td></tr>
          ))}
          {rows.length === 0 && <tr><td colSpan={4} className="muted">No data yet.</td></tr>}
        </tbody>
      </table>

      {showCreate && (
        <div className="modal-backdrop" onClick={() => setShowCreate(false)}>
          <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(420px,100%)" }}>
            <div className="modal-header">
              <h2>Create an alliance</h2>
              <button className="modal-close" onClick={() => setShowCreate(false)}>✕</button>
            </div>
            <div className="modal-body">
              <label className="found-field">
                <span>Tag (2–6 chars)</span>
                <input value={tag} maxLength={6} placeholder="TAG" onChange={e => setTag(e.target.value)} />
              </label>
              <label className="found-field">
                <span>Name (3–32 chars)</span>
                <input value={name} maxLength={32} placeholder="Alliance name" onChange={e => setName(e.target.value)} />
              </label>
              {err && <p className="found-warn">{err}</p>}
              <button className="btn" disabled={busy || tag.trim().length < 2 || name.trim().length < 3} onClick={submit}>
                {busy ? "Creating…" : "Found alliance"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
