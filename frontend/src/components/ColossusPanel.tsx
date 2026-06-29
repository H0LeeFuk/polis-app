import { useEffect, useState } from "react";
import { getColossus, attackColossus } from "../api";
import type { ColossusDto, UnitDto } from "../types";

const ELEMENT_GLYPH: Record<string, string> = { FIRE: "🔥", WIND: "🌬", EARTH: "🌍", WATER: "💧" };
const fmt = (n: number) => n >= 1000 ? (n / 1000).toFixed(n >= 100000 ? 0 : 1) + "k" : Math.floor(n).toString();
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

/** Resistance tier from a defence value vs the (constant) average bulk of 100 per element. */
function resLabel(def: number): { txt: string; cls: string } {
  if (def <= 70) return { txt: "Weak", cls: "res-weak" };
  if (def >= 130) return { txt: "Resists", cls: "res-strong" };
  return { txt: "Medium", cls: "res-mid" };
}

function countdown(iso: string): string {
  const s = Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
  const m = Math.floor(s / 60);
  return m > 0 ? `${m}m ${s - m * 60}s` : `${s}s`;
}

/**
 * Colossus detail + attack. Reward is split by each alliance's share of total damage and paid to
 * alliance treasuries — there is no last-hit bonus, so every attack is worthwhile.
 */
export default function ColossusPanel({ colossusId, myUnits, activeCityId, onClose, onChanged, setErr }: {
  colossusId: number; myUnits: UnitDto[]; activeCityId: number;
  onClose: () => void; onChanged: () => void; setErr: (s: string) => void;
}) {
  const [c, setC] = useState<ColossusDto | null>(null);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState("");
  const [, force] = useState(0);

  const load = () => getColossus(colossusId).then(setC).catch((e: any) => setErr(e.message));
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [colossusId]);
  // live countdown + periodic refresh of health / leaderboard while open
  useEffect(() => { const t = setInterval(() => { force(x => x + 1); load(); }, 5000); return () => clearInterval(t); /* eslint-disable-next-line */ }, [colossusId]);

  if (!c) return null;
  const hpPct = Math.max(0, Math.round((c.currentHealth / Math.max(1, c.maxHealth)) * 100));
  const ELEMS = ["FIRE", "WIND", "EARTH", "WATER"] as const;

  const send = async () => {
    const troops = Object.fromEntries(Object.entries(counts).filter(([, v]) => v > 0));
    if (!Object.keys(troops).length) { setNote("Select sea or flying units to send."); return; }
    setBusy(true); setNote("");
    try {
      const r = await attackColossus(colossusId, activeCityId, troops);
      setNote(`Strike dispatched — resolves in ~${Math.max(1, Math.round((r.travelSeconds ?? 0) / 60))}m. Damage will post to your alliance.`);
      setCounts({}); onChanged(); load();
    } catch (e: any) { setNote(e.message); }
    finally { setBusy(false); }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window colossus-window" onClick={e => e.stopPropagation()} style={{ width: "min(620px,100%)" }}>
        <div className="modal-header">
          <h2>🐙 {c.name} <small className="muted">· Colossus T{c.tier}</small></h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          {/* shared health pool + despawn countdown */}
          <div className="colossus-hpbar"><i style={{ width: hpPct + "%" }} /><span>{fmt(c.currentHealth)} / {fmt(c.maxHealth)} HP</span></div>
          <div className="muted" style={{ marginBottom: 10 }}>
            {c.status === "ROAMING" ? <>Roaming the middle ring · despawns in <b>{countdown(c.despawnAt)}</b></> : `Status: ${c.status}`}
          </div>

          {/* today's elemental defence profile — drives which race attacks best today */}
          <div className="br-section-label">Today's defences</div>
          <div className="colossus-defs">
            {ELEMS.map(e => { const r = resLabel(c.defense[e] ?? 100); return (
              <div className={"colossus-def " + r.cls} key={e}>
                <span className="cd-ico">{ELEMENT_GLYPH[e]}</span>
                <span className="cd-val">{c.defense[e]}</span>
                <span className="cd-tag">{r.txt}</span>
              </div>
            ); })}
          </div>
          <p className="muted" style={{ fontSize: 12 }}>Attack with the element it's <b>Weak</b> to for the most damage. Fairies (flying) and fleets can both engage.</p>

          {/* live per-alliance damage leaderboard */}
          <div className="br-section-label">Alliance damage — reward share</div>
          <div className="colossus-lb">
            {(c.leaderboard ?? []).length === 0 && <div className="muted">No damage yet — be the first to strike.</div>}
            {(c.leaderboard ?? []).map(row => (
              <div className="colossus-lb-row" key={row.allianceId}>
                <span>#{row.rank} [{row.allianceTag}] {row.allianceName}</span>
                <span className="muted">{fmt(row.damage)} · {row.sharePct}%</span>
              </div>
            ))}
          </div>
          <div className="muted" style={{ fontSize: 12, margin: "6px 0" }}>
            Your alliance: <b>{c.myAllianceSharePct}%</b> ({fmt(c.myAllianceDamage)} dmg) · pool {fmt(c.rewardPoolPerResource)} each of 🪵🪨🌾 → split by share to alliance treasuries.
          </div>

          {/* attack: pick fleet / flying units from the active city */}
          {c.status === "ROAMING" && (
            <>
              <div className="br-section-label">Send forces (from your selected city)</div>
              <div className="colossus-troops">
                {myUnits.length === 0 && <p className="muted">No troops in the selected city. Build a fleet or flying units first.</p>}
                {myUnits.map(u => (
                  <div className="ct-row" key={u.type}>
                    <span>{titleCase(u.type)} <small className="muted">×{u.count}</small></span>
                    <input type="number" min={0} max={u.count} value={counts[u.type] || 0}
                      onChange={e => setCounts({ ...counts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
                  </div>
                ))}
              </div>
              <button className="btn" disabled={busy} onClick={send}>⚔ Dispatch strike</button>
              {note && <p className="muted" style={{ marginTop: 8 }}>{note}</p>}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
