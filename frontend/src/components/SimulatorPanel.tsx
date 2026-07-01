import { useEffect, useState } from "react";
import { simulateCombat, importSpyForSim, getSpyReports, type SimSide, type SimResult } from "../api";
import type { SpyReportDto } from "../types";
import { UNIT_GLYPH } from "../movements";

const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const glyph = (t: string) => UNIT_GLYPH[t] ?? "⚔";
const ELEM = { FIRE: "🔥", WIND: "🌬", EARTH: "🌍", WATER: "💧" } as const;
const ELEM_ORDER = ["FIRE", "WIND", "EARTH", "WATER"];

const RACES = ["HUMANS", "GIANTS", "FAIRIES", "NEWTS"] as const;
const RACE_LABEL: Record<string, string> = { HUMANS: "Humans", GIANTS: "Giants", FAIRIES: "Fairies", NEWTS: "Newts" };
const SHARED_LAND = ["RAIDER", "WARDEN", "OUTRIDER"];
// per-race combatant roster, split by combat layer (transports excluded — they never fight)
const ROSTER: Record<string, { land: string[]; sea: string[] }> = {
  HUMANS:  { land: ["HOPLITE", "SWORDSMAN", "SPEARMAN", "ARCHER", "HORSEMAN", "CATAPULT", "FLAME_LEGION"], sea: ["TRIREME", "FIRE_RAM"] },
  GIANTS:  { land: ["BOULDER_THROWER", "TROLL", "STONE_GIANT", "COLOSSUS", "EARTHSHAKER"], sea: ["BULWARK_SHIP", "SIEGE_GALLEON"] },
  FAIRIES: { land: ["GLIMMER_GUARD", "SPRITE", "PIXIE_ARCHER", "MOTH_RIDER", "DRAGONFLY_SKIFF", "STORMCALLER"], sea: [] },
  NEWTS:   { land: ["MUDLING", "NEWT_SPEAR", "SNAPPER", "TIDE_RAIDER", "LEVIATHAN", "LEVIATHAN_RIDER"], sea: [] },
};
const rosterFor = (race: string, layer: "SEA" | "LAND") =>
  layer === "SEA" ? (ROSTER[race]?.sea ?? []) : [...(ROSTER[race]?.land ?? []), ...SHARED_LAND];

type SideState = { race: string; troops: Record<string, number>; hero: boolean; heroAttack: number; attackBuff: number; defenseBuff: number };
const freshSide = (race: string): SideState => ({ race, troops: {}, hero: false, heroAttack: 200, attackBuff: 1, defenseBuff: 1 });

/** Build vs. defender; run the REAL combat engine (pure mode) and see the exact outcome. */
export default function SimulatorPanel({ onClose }: { onClose: () => void }) {
  const [layer, setLayer] = useState<"SEA" | "LAND">("LAND");
  const [atk, setAtk] = useState<SideState>(freshSide("HUMANS"));
  const [def, setDef] = useState<SideState>(freshSide("GIANTS"));
  const [result, setResult] = useState<SimResult | null>(null);
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  const [spyReports, setSpyReports] = useState<SpyReportDto[]>([]);
  const [spyStale, setSpyStale] = useState<string | null>(null);

  useEffect(() => { getSpyReports().then(r => setSpyReports(r.filter(x => x.outcome === "SUCCESS"))).catch(() => {}); }, []);

  const toSide = (s: SideState, role: "atk" | "def"): SimSide => ({
    race: s.race,
    troops: Object.fromEntries(Object.entries(s.troops).filter(([, n]) => n > 0)),
    heroAttack: role === "atk" && s.hero ? s.heroAttack : undefined,
    attackBuff: s.attackBuff, defenseBuff: s.defenseBuff,
  });

  const run = async () => {
    setErr(""); setBusy(true);
    try { setResult(await simulateCombat(toSide(atk, "atk"), toSide(def, "def"), layer)); }
    catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };

  const importSpy = async (id: number) => {
    setErr("");
    try {
      const d = await importSpyForSim(id);
      setDef(s => ({ ...s, troops: { ...d.troops } }));
      setSpyStale(d.capturedAt);
    } catch (e: any) { setErr(e.message); }
  };

  const sidePanel = (s: SideState, setS: (u: SideState) => void, role: "atk" | "def") => {
    const roster = rosterFor(s.race, layer);
    return (
      <div className={"sim-side sim-" + role}>
        <h3>{role === "atk" ? "⚔ Attacker" : "🛡 Defender"}</h3>
        <label className="sim-field">Race
          <select value={s.race} onChange={e => setS({ ...s, race: e.target.value, troops: {} })}>
            {RACES.map(r => <option key={r} value={r}>{RACE_LABEL[r]}</option>)}
          </select>
        </label>
        <div className="sim-roster">
          {roster.length === 0 ? <p className="muted">No {layer.toLowerCase()} units for {RACE_LABEL[s.race]}.</p>
            : roster.map(u => (
              <div key={u} className="sim-unit">
                <span>{glyph(u)} {titleCase(u)}</span>
                <input type="number" min={0} value={s.troops[u] || 0}
                  onChange={e => setS({ ...s, troops: { ...s.troops, [u]: Math.max(0, +e.target.value) } })} />
              </div>
            ))}
        </div>
        {role === "atk" && (
          <label className="sim-field sim-hero">
            <input type="checkbox" checked={s.hero} onChange={e => setS({ ...s, hero: e.target.checked })} /> Hero leads
            {s.hero && <input type="number" min={0} title="Hero attack power" value={s.heroAttack}
              onChange={e => setS({ ...s, heroAttack: Math.max(0, +e.target.value) })} />}
          </label>
        )}
        <div className="sim-buffs">
          <label className="sim-field">Attack ×<input type="number" min={0} step={0.05} value={s.attackBuff}
            onChange={e => setS({ ...s, attackBuff: Math.max(0, +e.target.value) })} /></label>
          <label className="sim-field">Defense ×<input type="number" min={0} step={0.05} value={s.defenseBuff}
            onChange={e => setS({ ...s, defenseBuff: Math.max(0, +e.target.value) })} /></label>
        </div>
        {role === "def" && spyReports.length > 0 && (
          <label className="sim-field">Import from spy
            <select value="" onChange={e => { if (e.target.value) importSpy(+e.target.value); }}>
              <option value="">— pick a report —</option>
              {spyReports.map(r => <option key={r.id} value={r.id}>{r.targetCityName}</option>)}
            </select>
          </label>
        )}
        {role === "def" && spyStale && <div className="sim-stale">⚠ Intel from {new Date(spyStale).toLocaleString()} — real defenses may have changed.</div>}
      </div>
    );
  };

  const lossTable = (title: string, present: Record<string, number>, lost: Record<string, number>, surv: Record<string, number>, presentLabel: string) => {
    const types = Array.from(new Set([...Object.keys(present), ...Object.keys(lost), ...Object.keys(surv)]))
      .filter(t => (present[t] ?? 0) > 0 || (lost[t] ?? 0) > 0 || (surv[t] ?? 0) > 0);
    return (
      <div className="sim-losses">
        <h4>{title}</h4>
        <div className="sim-lrow sim-lhead"><span /><span>{presentLabel}</span><span>Lost</span><span>Left</span></div>
        {types.length === 0 ? <div className="sim-lrow muted"><span /><span>—</span><span>—</span><span>—</span></div>
          : types.map(t => (
            <div className="sim-lrow" key={t}>
              <span>{glyph(t)} {titleCase(t)}</span>
              <span>{present[t] ?? 0}</span>
              <span className="loss">{lost[t] ?? 0}</span>
              <span className="surv">{surv[t] ?? 0}</span>
            </div>
          ))}
      </div>
    );
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window sim-window" onClick={e => e.stopPropagation()} style={{ width: "min(900px,100%)" }}>
        <div className="modal-header">
          <h2>⚔📜 Combat Simulator</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="sim-toolbar">
            <div className="sim-layer">Layer:
              <button className={"sim-lbtn" + (layer === "LAND" ? " active" : "")} onClick={() => { setLayer("LAND"); setResult(null); }}>⚔ Land</button>
              <button className={"sim-lbtn" + (layer === "SEA" ? " active" : "")} onClick={() => { setLayer("SEA"); setResult(null); }}>⛵ Sea</button>
            </div>
            <span className="muted sim-disclaimer">Planning tool — sends no attack, costs nothing. Result is exact for these inputs.</span>
          </div>
          {err && <div className="hero-inline-err">{err}</div>}
          <div className="sim-sides">
            {sidePanel(atk, setAtk, "atk")}
            {sidePanel(def, setDef, "def")}
          </div>
          <button className="btn sim-run" disabled={busy} onClick={run}>{busy ? "Simulating…" : "⚔ Simulate"}</button>

          {result && (
            <div className="sim-result">
              <div className={"sim-winner " + (result.winner === "ATTACKER" ? "atk" : "def")}>
                {result.winner === "ATTACKER" ? "⚔ Attacker wins" : "🛡 Defender holds"}
                <small> · ratio {result.globalRatio} · ⚔ {result.attackerAttackPower} vs 🛡 {result.defenderDefencePower}</small>
              </div>
              <div className="sim-result-grid">
                {lossTable("Attacker", result.attacker.sent, result.attacker.lost, result.attacker.survived, "Sent")}
                {lossTable("Defender", result.defender.present, result.defender.lost, result.defender.survived, "Present")}
              </div>
              <div className="sim-elements">
                <h4>Elemental breakdown</h4>
                <div className="sim-erow sim-ehead"><span>Element</span><span>Attack</span><span>Defense</span></div>
                {ELEM_ORDER.map(el => (
                  <div className="sim-erow" key={el}>
                    <span>{ELEM[el as keyof typeof ELEM]} {titleCase(el)}</span>
                    <span>{result.attackByElement[el] ?? 0}</span>
                    <span>{result.defenseByElement[el] ?? 0}</span>
                  </div>
                ))}
              </div>
              <p className="muted sim-honesty">Exact for the inputs given. Imported spy intel can be stale — the real battle uses whatever the defender has at impact.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
