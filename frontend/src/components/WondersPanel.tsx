import { useEffect, useState } from "react";
import {
  getWorldState, getWonderLeaderboard, getMyAlliance,
  occupyWonder, attackWonder, withdrawWonder, investWonder, forceEndgame,
} from "../api";
import type { WorldEndgame, WonderDto, WonderLeader, UnitDto, Hero } from "../types";
import { UNIT_GLYPH, HeroPicker } from "../movements";

const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const glyph = (t: string) => UNIT_GLYPH[t?.toUpperCase()] ?? "⚔";
const fmt = (n: number) => n >= 10000 ? (n / 1000).toFixed(1) + "k" : Math.floor(n).toString();
const KIND_ICON: Record<string, string> = { LIGHTHOUSE: "🗼", COLOSSUS: "🗽", SANCTUM: "🏯" };

function clock(s: number) {
  if (s < 0) return "—";
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = Math.floor(s % 60);
  return h > 0 ? `${h}h ${m}m` : m > 0 ? `${m}m ${sec}s` : `${sec}s`;
}

export interface WonderCtx {
  myPlayerId: number; activeCityId: number; myUnits: UnitDto[]; heroes: Hero[];
  setErr: (s: string) => void; onChanged: () => void;
}

/** A floating endgame status bar shown over the world map: phase, win timer, leaderboard. */
export function EndgameBar({ onOpenWonder }: { now: number; onOpenWonder: (w: WonderDto) => void }) {
  const [st, setSt] = useState<WorldEndgame | null>(null);
  const [board, setBoard] = useState<WonderLeader[]>([]);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const load = () => { getWorldState().then(setSt).catch(() => {}); getWonderLeaderboard().then(setBoard).catch(() => {}); };
  useEffect(() => { load(); const t = setInterval(load, 15000); return () => clearInterval(t); }, []);
  if (!st) return null;

  const dev = async () => { setBusy(true); try { await forceEndgame(); load(); } finally { setBusy(false); } };

  if (st.phase === "FINISHED") {
    return (
      <div className="endgame-bar won">
        <div className="eg-title">🏆 World Won</div>
        <div className="eg-sub"><b>{st.winnerAllianceName}</b> holds all three Wonders of the Aegean. The age is theirs.</div>
      </div>
    );
  }

  const left = st.consolidationSecondsLeft;
  return (
    <div className={"endgame-bar" + (st.phase === "ENDGAME" ? " active" : "")}>
      <button className="eg-head" onClick={() => setOpen(o => !o)}>
        {st.phase === "GROWTH" ? (
          <><span className="eg-title">⏳ Age of Growth</span>
            <span className="eg-sub">{st.cityCount}/{st.cityThreshold} cities · day {st.worldAgeDays}/{st.daysThreshold} to endgame</span></>
        ) : (
          <><span className="eg-title">⚔ Endgame: Wonders of the Aegean</span>
            <span className="eg-sub">{left >= 0
              ? <><b>{st.consolidationAllianceName}</b> consolidating — {clock(left)} to victory</>
              : "Seize and hold all three Wonders at max level to win"}</span></>
        )}
        <span className="eg-caret">{open ? "▾" : "▸"}</span>
      </button>

      {st.phase === "ENDGAME" && left >= 0 && (
        <div className="eg-timer"><i style={{ width: (100 * (st.consolidationTotalSeconds - left) / st.consolidationTotalSeconds) + "%" }} /></div>
      )}

      {open && (
        <div className="eg-body">
          <div className="eg-wonders">
            {st.wonders.map(w => (
              <button key={w.id} className={"eg-wonder tone-" + w.status.toLowerCase()} onClick={() => onOpenWonder(w)}>
                <span className="eg-w-ico">{KIND_ICON[w.kind]}</span>
                <span className="eg-w-main">
                  <b>{w.name}</b>
                  <small>{w.status === "CONTROLLED" ? `${w.controllingAllianceName} · Lv ${w.level}/${w.maxLevel}`
                    : w.status === "DORMANT" ? "Dormant" : titleCase(w.status) + ` · Lv ${w.level}`}</small>
                </span>
                <span className="eg-w-lvl"><i style={{ width: (100 * w.level / w.maxLevel) + "%" }} /></span>
              </button>
            ))}
          </div>
          {board.length > 0 && (
            <div className="eg-board">
              <div className="eg-board-h">Alliance leaderboard</div>
              {board.map((r, i) => (
                <div key={r.allianceId} className="eg-board-row">
                  <span>{i + 1}. {r.allianceName}</span>
                  <span className="muted">{r.wondersHeld} held · {r.totalLevels} lvls</span>
                </div>
              ))}
            </div>
          )}
          {st.phase === "GROWTH" && (
            <button className="btn ghost eg-dev" disabled={busy} onClick={dev}>🔧 Force endgame (dev)</button>
          )}
        </div>
      )}
    </div>
  );
}

/** Endgame info as a standalone menu panel (opened from the side nav), not a floating map bar. */
export function EndgamePanel({ ctx }: { ctx: WonderCtx }) {
  const [st, setSt] = useState<WorldEndgame | null>(null);
  const [board, setBoard] = useState<WonderLeader[]>([]);
  const [sel, setSel] = useState<WonderDto | null>(null);
  const [busy, setBusy] = useState(false);
  const load = () => { getWorldState().then(setSt).catch(() => {}); getWonderLeaderboard().then(setBoard).catch(() => {}); };
  useEffect(() => { load(); const t = setInterval(load, 15000); return () => clearInterval(t); }, []);
  if (!st) return <p className="muted">Consulting the oracle…</p>;

  const dev = async () => { setBusy(true); try { await forceEndgame(); load(); } finally { setBusy(false); } };
  const left = st.consolidationSecondsLeft;

  return (
    <div className="endgame-panel">
      {st.phase === "FINISHED" ? (
        <div className="endgame-bar won">
          <div className="eg-title">🏆 World Won</div>
          <div className="eg-sub"><b>{st.winnerAllianceName}</b> holds all three Wonders of the Aegean. The age is theirs.</div>
        </div>
      ) : (
        <>
          <div className="eg-headline">
            {st.phase === "GROWTH" ? (
              <><span className="eg-title">⏳ Age of Growth</span>
                <span className="eg-sub">{st.cityCount}/{st.cityThreshold} cities · day {st.worldAgeDays}/{st.daysThreshold} to endgame</span></>
            ) : (
              <><span className="eg-title">⚔ Endgame: Wonders of the Aegean</span>
                <span className="eg-sub">{left >= 0
                  ? <><b>{st.consolidationAllianceName}</b> consolidating — {clock(left)} to victory</>
                  : "Seize and hold all three Wonders at max level to win"}</span></>
            )}
          </div>
          {st.phase === "ENDGAME" && left >= 0 && (
            <div className="eg-timer"><i style={{ width: (100 * (st.consolidationTotalSeconds - left) / st.consolidationTotalSeconds) + "%" }} /></div>
          )}
          <div className="eg-wonders">
            {st.wonders.map(w => (
              <button key={w.id} className={"eg-wonder tone-" + w.status.toLowerCase()} onClick={() => setSel(w)}>
                <span className="eg-w-ico">{KIND_ICON[w.kind]}</span>
                <span className="eg-w-main">
                  <b>{w.name}</b>
                  <small>{w.status === "CONTROLLED" ? `${w.controllingAllianceName} · Lv ${w.level}/${w.maxLevel}`
                    : w.status === "DORMANT" ? "Dormant" : titleCase(w.status) + ` · Lv ${w.level}`}</small>
                </span>
                <span className="eg-w-lvl"><i style={{ width: (100 * w.level / w.maxLevel) + "%" }} /></span>
              </button>
            ))}
          </div>
          {board.length > 0 && (
            <div className="eg-board">
              <div className="eg-board-h">Alliance leaderboard</div>
              {board.map((r, i) => (
                <div key={r.allianceId} className="eg-board-row">
                  <span>{i + 1}. {r.allianceName}</span>
                  <span className="muted">{r.wondersHeld} held · {r.totalLevels} lvls</span>
                </div>
              ))}
            </div>
          )}
          {st.phase === "GROWTH" && (
            <button className="btn ghost eg-dev" disabled={busy} onClick={dev}>🔧 Force endgame (dev)</button>
          )}
        </>
      )}
      {sel && <WonderModal wonder={sel} ctx={ctx} onClose={() => setSel(null)} onChanged={load} />}
    </div>
  );
}

/** Capture / reinforce / invest UI for a single Wonder. */
export function WonderModal({ wonder, ctx, onClose, onChanged }: {
  wonder: WonderDto; ctx: WonderCtx; onClose: () => void; onChanged: () => void;
}) {
  const [w, setW] = useState<WonderDto>(wonder);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [heroId, setHeroId] = useState<number | null>(null);
  const [invest, setInvest] = useState(0);
  const [myAlly, setMyAlly] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  const reload = () => getWorldState().then(s => { const f = s.wonders.find(x => x.id === w.id); if (f) setW(f); }).catch(() => {});
  useEffect(() => { getMyAlliance().then(a => setMyAlly(a?.id ?? null)).catch(() => setMyAlly(null)); }, []);

  const mine = myAlly != null && w.controllingAllianceId === myAlly;
  const selected = Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
  const hasTroops = Object.keys(selected).length > 0;
  const body = () => ({ cityId: ctx.activeCityId, troops: selected, heroId });

  const run = async (fn: () => Promise<any>) => {
    setBusy(true); ctx.setErr("");
    try { await fn(); setCounts({}); await reload(); onChanged(); ctx.onChanged(); }
    catch (e: any) { ctx.setErr(e.message); } finally { setBusy(false); }
  };

  const dormant = w.status === "DORMANT";
  const invested = Math.min(w.investedWood, w.investedStone, w.investedWheat);
  const pct = w.nextLevelCost > 0 ? Math.min(100, Math.round(100 * invested / w.nextLevelCost)) : 100;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(500px,100%)" }}>
        <div className="modal-header">
          <h2>{KIND_ICON[w.kind]} {w.name}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body"><div className="popup-panel">
          <div className="node-detail-grid">
            <div><strong>Status</strong><span className={"node-badge tone-" + w.status.toLowerCase()}>{titleCase(w.status)}</span></div>
            <div><strong>Holder</strong><span>{w.controllingAllianceName ?? "Unclaimed"}</span></div>
            <div><strong>Level</strong><span>{w.level} / {w.maxLevel}</span></div>
            <div><strong>Garrison</strong><span>{w.garrisonPop} pop</span></div>
          </div>
          <div className="wonder-lvlbar"><i style={{ width: (100 * w.level / w.maxLevel) + "%" }} /></div>

          <div className="node-garrison">
            {Object.entries(w.garrison).filter(([, q]) => q > 0).map(([t, q]) => <span key={t}>{glyph(t)} {q}</span>)}
            {Object.keys(w.garrison).length === 0 && <span className="muted">No troops guarding.</span>}
          </div>

          {dormant ? (
            <p className="muted">This Wonder lies dormant. It awakens when the world enters its endgame.</p>
          ) : mine ? (
            <>
              {/* invest to raise the level */}
              {w.level < w.maxLevel ? (
                <div className="wonder-invest">
                  <div className="wonder-invest-row">
                    <span>Raise to Lv {w.level + 1}: <b>{fmt(invested)}</b> / {fmt(w.nextLevelCost)} each</span>
                  </div>
                  <div className="wonder-lvlbar small"><i style={{ width: pct + "%" }} /></div>
                  <div className="raid-row">
                    <span>Invest (each resource)</span>
                    <input type="number" min={0} value={invest}
                      onChange={e => setInvest(Math.max(0, +e.target.value))} />
                  </div>
                  <button className="btn" disabled={busy || invest <= 0}
                    onClick={() => run(async () => { await investWonder(w.id, ctx.activeCityId, invest); setInvest(0); })}>
                    🏗 Invest from active city
                  </button>
                </div>
              ) : <p className="go">★ Wonder at maximum level — hold it to win the world.</p>}

              <TroopSelector myUnits={ctx.myUnits} counts={counts} setCounts={setCounts} label="Reinforce from active city:" />
              <div className="node-actions">
                <button className="btn" disabled={busy || !hasTroops} onClick={() => run(() => occupyWonder(w.id, body()))}>➕ Reinforce</button>
                <button className="btn ghost" disabled={busy} onClick={() => run(() => withdrawWonder(w.id))}>↩ Withdraw all</button>
              </div>
            </>
          ) : w.status === "CONTROLLED" ? (
            <>
              <TroopSelector myUnits={ctx.myUnits} counts={counts} setCounts={setCounts} label="Assault with troops:" />
              <HeroPicker heroes={ctx.heroes} value={heroId} onChange={setHeroId} />
              <button className="btn" disabled={busy || !hasTroops} onClick={() => run(() => attackWonder(w.id, body()))}>⚔ Assault Wonder</button>
            </>
          ) : (
            <>
              <TroopSelector myUnits={ctx.myUnits} counts={counts} setCounts={setCounts} label="Occupy with troops:" />
              <HeroPicker heroes={ctx.heroes} value={heroId} onChange={setHeroId} />
              <button className="btn" disabled={busy || !hasTroops} onClick={() => run(() => occupyWonder(w.id, body()))}>🚩 Occupy Wonder</button>
            </>
          )}
        </div></div>
      </div>
    </div>
  );
}

function TroopSelector({ myUnits, counts, setCounts, label }: {
  myUnits: UnitDto[]; counts: Record<string, number>; setCounts: (c: Record<string, number>) => void; label: string;
}) {
  if (myUnits.length === 0) return <p className="muted">No troops in your active city.</p>;
  return (
    <>
      <p className="muted">{label}</p>
      {myUnits.map(u => (
        <div key={u.type} className="raid-row">
          <span>{glyph(u.type)} {titleCase(u.type)} <small className="muted">({u.count})</small></span>
          <input type="number" min={0} max={u.count} value={counts[u.type] || 0}
            onChange={e => setCounts({ ...counts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
        </div>
      ))}
    </>
  );
}
