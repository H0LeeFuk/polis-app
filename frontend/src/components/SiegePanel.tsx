import { useEffect, useState } from "react";
import { getMySieges, reinforceSiege, attackSiege, withdrawSiege, chooseRace, getSiegeMovements } from "../api";
import type { SiegeData, UnitDto, Hero, Movement } from "../types";
import { troopSummary, kindMeta, fmtEta } from "../movements";
import { RACES } from "./FoundCity";

function countdown(iso: string | null): string {
  if (!iso) return "—";
  let s = Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
  const h = Math.floor(s / 3600); s -= h * 3600; const m = Math.floor(s / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m ${s - m * 60}s`;
}
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

/**
 * Siege command center (both perspectives). Lists the player's sieges — ones they're running and
 * ones laid on their cities — and exposes the live actions: reinforce (besieger/ally), break by land
 * or sea (defender/ally), and withdraw leftovers. Sends march from the active city.
 */
export default function SiegePanel({ originCityId, originName, myUnits, heroes, onClose, onChanged }: {
  originCityId: number; originName: string; myUnits: UnitDto[]; heroes: Hero[];
  onClose: () => void; onChanged?: () => void;
}) {
  const [sieges, setSieges] = useState<SiegeData[] | null>(null);
  const [tab, setTab] = useState<"mine" | "ally">("mine");
  const [err, setErr] = useState("");
  const [, force] = useState(0);
  useEffect(() => { const t = setInterval(() => force(x => x + 1), 1000); return () => clearInterval(t); }, []);

  const load = () => getMySieges().then(setSieges).catch(e => setErr(e.message));
  useEffect(() => { load(); }, []);

  const refresh = async () => { await load(); onChanged?.(); };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(640px,100%)" }}>
        <div className="modal-header">
          <h2>⚑ Sieges</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          {err && <div className="hero-inline-err">{err}</div>}
          {!sieges ? <p className="muted">Loading…</p>
            : (() => {
                const mine = sieges.filter(s => !s.isAllianceSiege);
                const ally = sieges.filter(s => s.isAllianceSiege);
                const card = (s: SiegeData) => (
                  <SiegeCard key={s.id} s={s} originCityId={originCityId} originName={originName}
                    myUnits={myUnits} heroes={heroes} setErr={setErr} onChanged={refresh} />
                );
                const shown = tab === "mine" ? mine : ally;
                return (
                  <>
                    <div className="siege-tabs">
                      <button className={"siege-tab" + (tab === "mine" ? " active" : "")} onClick={() => setTab("mine")}>
                        ⚔ My sieges{mine.length ? ` (${mine.length})` : ""}</button>
                      <button className={"siege-tab" + (tab === "ally" ? " active" : "")} onClick={() => setTab("ally")}>
                        🤝 Alliance sieges{ally.length ? ` (${ally.length})` : ""}</button>
                    </div>
                    {shown.length ? shown.map(card)
                      : <p className="muted siege-group-empty">{tab === "mine"
                          ? "None — you're neither besieging nor besieged. Launch one from the World map by attacking with a hero and choosing “⚑ Lay siege”."
                          : "No alliance sieges right now."}</p>}
                  </>
                );
              })()}
        </div>
      </div>
    </div>
  );
}

function SiegeCard({ s, originCityId, originName, myUnits, heroes, setErr, onChanged }: {
  s: SiegeData; originCityId: number; originName: string; myUnits: UnitDto[]; heroes: Hero[];
  setErr: (m: string) => void; onChanged: () => void;
}) {
  const [mode, setMode] = useState<"none" | "reinforce" | "break">("none");
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [heroId, setHeroId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [showMoves, setShowMoves] = useState(false);
  const [moves, setMoves] = useState<Movement[] | null>(null);

  // the full besieging force = land troops + ships anchoring the blockade
  const besiegers = { ...(s.besiegingTroops ?? {}), ...(s.besiegingShips ?? {}) };
  const toggleMoves = async () => {
    const next = !showMoves; setShowMoves(next);
    if (next) { try { setMoves(await getSiegeMovements(s.id)); } catch (e: any) { setErr(e.message); } }
  };

  const units = () => Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
  const act = async (fn: () => Promise<unknown>) => {
    setErr(""); setBusy(true);
    try { await fn(); setMode("none"); setCounts({}); setHeroId(null); onChanged(); }
    catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };

  const active = s.status === "ACTIVE";
  const broken = s.status === "BROKEN";

  return (
    <div className={"siege-card st-" + s.status.toLowerCase()}>
      <div className="siege-card-head">
        <b>{s.isBesieger ? "⚔ Your siege of" : s.isDefender ? "🛡 Siege on your" : "⚑ Siege of"} {s.cityName}</b>
        <span className={"siege-badge " + s.status.toLowerCase()}>
          {active ? `ends in ${countdown(s.endsAt)}` : s.status === "BROKEN" ? "BROKEN" : "CONQUERED"}
        </span>
      </div>
      <div className="muted siege-sub">
        Besieger: {s.besiegingPlayer ?? "—"}{s.heroName ? ` · Hero ${s.heroName} (Lv ${s.heroLevel}) locked in` : ""}
      </div>

      {/* the two break-condition locks — losing EITHER ends the siege */}
      <div className="siege-locks">
        <div className={"siege-lock " + (s.troopsRemaining > 0 ? "ok" : "gone")}>
          🪖 Troops: <b>{s.troopsRemaining}</b> <small className="muted">land lock</small>
        </div>
        <div className={"siege-lock " + (s.shipsRemaining > 0 ? "ok" : "gone")}>
          ⛵ Ships: <b>{s.shipsRemaining}</b> <small className="muted">sea lock</small>
        </div>
      </div>
      <p className="muted siege-hint">Destroy <b>either</b> the troops <b>or</b> the ships to break the siege.</p>

      {/* full besieging force — every unit locked into the siege */}
      <div className="siege-force">
        <span className="siege-force-label">Besieging force:</span>
        {troopSummary(besiegers).length
          ? troopSummary(besiegers).map(t => (
            <span key={t.type} className="siege-unit" title={titleCase(t.type)}>{t.glyph} {t.n}</span>))
          : <span className="muted">— none remaining</span>}
      </div>

      <button className="btn ghost tiny siege-moves-toggle" onClick={toggleMoves}>
        {showMoves ? "▾" : "▸"} Movements to {s.cityName}
      </button>
      {showMoves && (
        <div className="siege-moves">
          {moves == null ? <p className="muted">Loading…</p>
            : moves.length === 0 ? <p className="muted">No armies currently heading to {s.cityName}.</p>
              : moves.map(m => {
                const meta = kindMeta(m);
                return (
                  <div key={m.id} className={"siege-move " + meta.cls}>
                    <span className="sm-route">{meta.icon} {m.originCity ?? "?"} → <b>{m.targetCity ?? s.cityName}</b>
                      {m.hostile && <small className="muted"> from {m.owner}</small>}</span>
                    <span className="sm-troops">{m.unitsKnown
                      ? (troopSummary(m.units).map(t => <span key={t.type} title={t.type}>{t.glyph}{t.n}</span>))
                      : <span className="muted">Unknown</span>}</span>
                    <span className="sm-eta">{fmtEta(m.arriveAt, Date.now())}</span>
                  </div>
                );
              })}
        </div>
      )}

      {broken && <p className="muted">Siege broken — surviving besiegers still occupy {s.cityName} until withdrawn.</p>}

      <div className="siege-actions">
        {s.canReinforce && <button className="btn ghost" disabled={busy} onClick={() => setMode(mode === "reinforce" ? "none" : "reinforce")}>➕ Reinforce</button>}
        {s.canBreak && <button className="btn ghost" disabled={busy} onClick={() => setMode(mode === "break" ? "none" : "break")}>⚔ Attack the siege</button>}
        {s.canWithdraw && <button className="btn ghost danger" disabled={busy} onClick={() => act(() => withdrawSiege(s.id))}>↩ Withdraw</button>}
      </div>

      {mode !== "none" && (
        <div className="siege-send">
          <p className="muted">
            {mode === "reinforce"
              ? `Send troops/ships from ${originName} to join the siege (does not reset the 8h clock).`
              : `Attack from ${originName}. Send LAND troops to destroy their troops, or SHIPS to sink their fleet — either breaks the siege.`}
          </p>
          {myUnits.length === 0 ? <p className="muted">No troops in {originName}.</p> : myUnits.map(u => (
            <div key={u.type} className="raid-row">
              <span>{titleCase(u.type)} <small className="muted">({u.count})</small></span>
              <input type="number" min={0} max={u.count} value={counts[u.type] || 0}
                onChange={e => setCounts({ ...counts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
            </div>
          ))}
          {mode === "break" && (
            <label className="siege-hero-pick">Lead with hero:
              <select value={heroId ?? ""} onChange={e => setHeroId(e.target.value ? +e.target.value : null)}>
                <option value="">None</option>
                {heroes.map(h => <option key={h.id} value={h.id}>{h.name} (Lv {h.level})</option>)}
              </select>
            </label>
          )}
          <button className="btn" disabled={busy || Object.keys(units()).length === 0} onClick={() =>
            act(() => mode === "reinforce"
              ? reinforceSiege(s.id, originCityId, units())
              : attackSiege(s.id, originCityId, units(), heroId))}>
            {mode === "reinforce" ? "🚩 Send reinforcements" : "⚔ Launch break attempt"}
          </button>
        </div>
      )}
    </div>
  );
}

/**
 * Shown to the new owner of a freshly conquered city: pick the race it will adopt going forward.
 * MANDATORY — there is no dismiss (no backdrop close, no ✕): closing must never silently lock in the
 * previous owner's inherited race. The modal only goes away once a race is actually chosen.
 */
export function RaceChoiceModal({ cityId, cityName, onChosen }: {
  cityId: number; cityName: string; onChosen: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const pick = async (race: string) => {
    setErr(""); setBusy(true);
    try { await chooseRace(cityId, race); onChosen(); }
    catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };
  return (
    <div className="modal-backdrop">
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(520px,100%)" }}>
        <div className="modal-header"><h2>🏰 {cityName} conquered — choose its race</h2></div>
        <div className="modal-body">
          <p className="muted">The city is yours. You must pick the race it will adopt — this sets its bonuses and roster going forward.</p>
          {err && <div className="hero-inline-err">{err}</div>}
          <div className="race-choice-grid">
            {RACES.map(r => (
              <button key={r.id} className="btn race-choice" disabled={busy} onClick={() => pick(r.id)}>
                {r.name}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
