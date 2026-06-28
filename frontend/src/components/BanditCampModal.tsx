import { useEffect, useState } from "react";
import { getBanditCamp, attackBanditCamp } from "../api";
import type { BanditCamp, BanditAttackResult, UnitDto } from "../types";
import { UNIT_GLYPH, UNIT_DEX } from "../movements";

const RES_GLYPH: Record<string, string> = { wood: "🪵", stone: "🪨", silver: "🪙" };
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const glyph = (t: string) => UNIT_GLYPH[t?.toUpperCase()] ?? "⚔";
const rewardGlyph = (k: string) => RES_GLYPH[k.toLowerCase()] ?? glyph(k);

function countdown(iso: string | null): string {
  if (!iso) return "";
  let s = Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
  const h = Math.floor(s / 3600); s -= h * 3600; const m = Math.floor(s / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m ${s - m * 60}s`;
}

export default function BanditCampModal({ islandId, activeCityId, cityOnIsland, myUnits, onClose, onChanged, setErr }: {
  islandId: number; activeCityId: number; cityOnIsland: boolean; myUnits: UnitDto[];
  onClose: () => void; onChanged: () => void; setErr: (s: string) => void;
}) {
  const [camp, setCamp] = useState<BanditCamp | null>(null);
  const [loadErr, setLoadErr] = useState("");
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [result, setResult] = useState<BanditAttackResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [, force] = useState(0);

  const load = () => getBanditCamp(islandId).then(c => { setCamp(c); setLoadErr(""); }).catch(e => setLoadErr(e.message || "Failed to load bandit camp"));
  useEffect(() => { load(); }, [islandId]);
  useEffect(() => { const t = setInterval(() => force(x => x + 1), 1000); return () => clearInterval(t); }, []);

  if (!camp) {
    return (
      <div className="modal-backdrop" onClick={onClose}>
        <div className="modal-window bandit-modal" onClick={e => e.stopPropagation()} style={{ width: "min(600px,100%)" }}>
          <div className="modal-header">
            <h2>🏴‍☠️ Bandit Camp</h2>
            <button className="modal-close" onClick={onClose}>✕</button>
          </div>
          <div className="modal-body">
            {loadErr ? <p className="hero-inline-err">{loadErr}</p> : <p className="muted">Scouting the camp…</p>}
          </div>
        </div>
      </div>
    );
  }
  const defeated = camp.status === "DEFEATED";
  const totalAttack = Object.entries(counts).reduce((a, [t, n]) => a + (UNIT_DEX[t.toUpperCase()]?.atk ?? 0) * (n > 0 ? n : 0), 0);
  const selected = Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
  const canAttack = !defeated && cityOnIsland && Object.keys(selected).length > 0 && !busy;

  const attack = async () => {
    setBusy(true); setErr(""); setResult(null);
    try {
      const r = await attackBanditCamp(islandId, activeCityId, selected);
      setResult(r); setCounts({}); await load(); onChanged();
    } catch (e: any) { setErr(e.message); }
    finally { setBusy(false); }
  };

  const etaSeconds = result ? Math.max(0, Math.round((new Date(result.arriveAt).getTime() - Date.now()) / 1000)) : 0;
  const etaLabel = etaSeconds >= 60 ? `${Math.floor(etaSeconds / 60)}m ${etaSeconds % 60}s` : `${etaSeconds}s`;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window bandit-modal" onClick={e => e.stopPropagation()} style={{ width: "min(600px,100%)" }}>
        <div className="modal-header">
          <h2>🏴‍☠️ Bandit Camp — Level {camp.currentLevel} / {camp.maxLevel}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          {/* level progress dots */}
          <div className="bandit-dots">
            {Array.from({ length: camp.maxLevel }).map((_, i) => {
              const lv = i + 1;
              const cls = lv < camp.currentLevel ? "done" : lv === camp.currentLevel && !defeated ? "current" : "locked";
              return <span key={lv} className={"bandit-dot " + cls}>{lv}</span>;
            })}
          </div>

          <div className={"bandit-status " + (defeated ? "defeated" : "active")}>
            {defeated ? `DEFEATED — respawns in ${countdown(camp.respawnAt)}` : "ACTIVE"}
          </div>
          {camp.description && <p className="muted bandit-desc">{camp.description}</p>}

          {!defeated && (
            <>
              {/* enemy forces */}
              <div className="br-section-label">Enemy Forces</div>
              <div className="bandit-forces">
                {Object.entries(camp.defenderTroops ?? {}).map(([t, q]) => (
                  <div className="bandit-force" key={t}><span>{glyph(t)} {titleCase(t)}</span><b>{q}</b></div>
                ))}
              </div>

              {/* rewards */}
              <div className="br-section-label">Rewards</div>
              <div className="bandit-rewards">
                {Object.entries(camp.rewardPayload ?? {}).map(([k, v]) => (
                  <span key={k} className="bandit-reward">{rewardGlyph(k)} {v} {RES_GLYPH[k.toLowerCase()] ? titleCase(k) : titleCase(k)}</span>
                ))}
              </div>

              {/* attack */}
              <div className="br-section-label">Send Attack</div>
              {!cityOnIsland ? (
                <p className="muted">Switch to one of your cities on this island to attack.</p>
              ) : myUnits.length === 0 ? (
                <p className="muted">No troops in your active city.</p>
              ) : (
                <>
                  {myUnits.map(u => (
                    <div key={u.type} className="raid-row">
                      <span>{glyph(u.type)} {titleCase(u.type)} <small className="muted">({u.count})</small></span>
                      <input type="number" min={0} max={u.count} value={counts[u.type] || 0}
                        onChange={e => setCounts({ ...counts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
                    </div>
                  ))}
                  <div className="bandit-power">Total attack power: <b>{totalAttack.toLocaleString()}</b></div>
                  <button className="btn" disabled={!canAttack} onClick={attack}>{busy ? "Dispatching…" : "⚔ Send Attack!"}</button>
                </>
              )}
            </>
          )}

          {/* dispatch confirmation — troops are marching; outcome lands as a battle report */}
          {result && (
            <div className="bandit-result win">
              <h3>⚔ Troops marching!</h3>
              <div>Your army is on its way to the camp.</div>
              <div className="muted">Arrives in {etaLabel}{etaSeconds === 0 ? " — check your battle reports" : ""}.</div>
              <div className="muted bandit-desc">On victory the survivors carry the loot back home.</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
