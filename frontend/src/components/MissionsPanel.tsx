import { useEffect, useState } from "react";
import { getMissions, claimMission } from "../api";
import { useDraggable } from "../useDraggable";
import type { Mission, MissionsData } from "../types";

const REWARD_GLYPH: Record<string, string> = { wood: "🪵", stone: "🪨", wheat: "🌾", silver: "🌾", heroXp: "✨" };
const rewardText = (r: Record<string, number>) =>
  Object.entries(r).map(([k, v]) => `${REWARD_GLYPH[k] ?? ""}${v} ${k === "heroXp" ? "XP" : k}`).join(" · ");

export default function MissionsPanel({ onClose, onChanged }: { onClose: () => void; onChanged?: () => void }) {
  const win = useDraggable<HTMLDivElement>();
  const [data, setData] = useState<MissionsData | null>(null);
  const [err, setErr] = useState("");
  const [celebrate, setCelebrate] = useState<string | null>(null);

  const load = () => getMissions().then(setData).catch(e => setErr(e.message));
  useEffect(() => { load(); }, []);

  const claim = async (m: Mission) => {
    setErr("");
    try {
      const res = await claimMission(m.missionId);
      if (res.unlockedHero) setCelebrate(res.unlockedHero.name);
      await load(); onChanged?.();
    } catch (e: any) { setErr(e.message); }
  };

  return (
    <div className="mvov-backdrop" onClick={onClose}>
      <div className="mvov missions-panel" ref={win} onClick={e => e.stopPropagation()}>
        <div className="mvov-head">
          <h2>📜 Missions</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="missions-body">
          {err && <div className="hero-inline-err">{err}</div>}
          {celebrate && (
            <div className="mission-celebrate">
              🎉 <b>{celebrate}</b> has joined your cause! Open the Heroes panel to deploy her.
              <button className="btn ghost" onClick={() => setCelebrate(null)}>Dismiss</button>
            </div>
          )}
          {!data ? <p className="muted">Loading…</p> : (
            <>
              <p className="muted">Starter chain — {data.starterDone} / {data.starterTotal} complete. Finish them all to recruit Titania.</p>
              <div className="mission-list">
                {data.missions.map(m => <MissionCard key={m.missionId} m={m} onClaim={() => claim(m)} />)}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function MissionCard({ m, onClaim }: { m: Mission; onClaim: () => void }) {
  const pct = Math.min(100, Math.round((m.progress / Math.max(1, m.target)) * 100));
  const capstone = m.unlocksHeroKey != null;
  const cls = "mission-card st-" + m.status.toLowerCase() + (capstone ? " capstone" : "");
  return (
    <div className={cls}>
      <div className="mission-head">
        <span className="mission-order">{m.status === "COMPLETED" || m.status === "CLAIMED" ? "✓" : m.order}</span>
        <h3>{m.title}{capstone && <span className="mission-marquee"> · Recruit Titania the Fairy 🧚</span>}</h3>
        <span className={"mission-badge st-" + m.status.toLowerCase()}>{m.status}</span>
      </div>
      <p className="muted">{m.description}</p>
      {m.status === "LOCKED" ? (
        <div className="muted mission-locked">🔒 Complete the previous mission first</div>
      ) : m.status === "CLAIMED" ? (
        <div className="muted">Reward claimed.</div>
      ) : (
        <>
          {m.status !== "COMPLETED" && (
            <div className="mission-progress"><div className="bar"><i style={{ width: pct + "%" }} /></div>
              <span>{m.progress} / {m.target}</span></div>
          )}
          <div className="mission-reward">🎁 {rewardText(m.rewards) || "—"}</div>
          {m.status === "COMPLETED" && <button className="btn" onClick={onClaim}>Claim reward</button>}
        </>
      )}
    </div>
  );
}
