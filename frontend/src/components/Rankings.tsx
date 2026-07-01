import { useEffect, useState } from "react";
import { getRankings } from "../api";
import type { RankRow } from "../types";
import ProfilePanel from "./ProfilePanel";

const TABS: [string, string][] = [
  ["points", "Players · points"], ["combat", "Players · combat"],
  ["alliancePoints", "Alliances · points"], ["alliances", "Alliances · combat"],
];

export default function Rankings({ onGoCity }: { onGoCity?: (cityId: number) => void }) {
  const [tab, setTab] = useState("points");
  const [rows, setRows] = useState<RankRow[]>([]);
  const [viewId, setViewId] = useState<number | null>(null);   // player profile open

  useEffect(() => { getRankings(tab).then(setRows).catch(() => setRows([])); }, [tab]);

  return (
    <div className="panel">
      <div className="tabs">
        {TABS.map(([k, label]) => (
          <button key={k} className={"tab" + (tab === k ? " active" : "")} onClick={() => setTab(k)}>{label}</button>
        ))}
      </div>
      <table>
        <thead><tr><th>#</th><th>Name</th><th>Score</th><th></th></tr></thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              <td>{i + 1}</td>
              <td>{r.playerId != null
                ? <a className="rank-name-link" onClick={() => setViewId(r.playerId!)}>{r.name}</a>
                : r.name}</td>
              <td>{r.value.toLocaleString()}</td>
              <td className="muted">{r.sub}</td>
            </tr>
          ))}
          {rows.length === 0 && <tr><td colSpan={4} className="muted">No data yet.</td></tr>}
        </tbody>
      </table>
      {viewId != null && <ProfilePanel playerId={viewId} onClose={() => setViewId(null)} onGoCity={onGoCity} />}
    </div>
  );
}
