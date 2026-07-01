import { useEffect, useState } from "react";
import { useDraggable } from "../useDraggable";
import { getBanditTower, getBanditTowerLevels, attackBanditTower } from "../api";
import type { BanditTowerState, BanditTowerLevelRow, BanditTowerAttackResult, CityDetail, Hero } from "../types";
import { UNIT_GLYPH, ELEMENT_GLYPH, HeroPicker } from "../movements";

const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const glyph = (t: string) => UNIT_GLYPH[t?.toUpperCase()] ?? "⚔";
const RES_GLYPH: Record<string, string> = { WOOD: "🪵", STONE: "🪨", WHEAT: "🌾" };
const RARITY_CLASS: Record<string, string> = { COMMON: "rar-common", RARE: "rar-rare", EPIC: "rar-epic", LEGENDARY: "rar-legendary" };

function troopChips(units?: Record<string, number> | null) {
  if (!units) return null;
  const e = Object.entries(units).filter(([, n]) => n > 0);
  if (!e.length) return <span className="muted">—</span>;
  return <>{e.map(([t, n]) => <span key={t} className="bt-troop" title={titleCase(t)}>{glyph(t)} {n}</span>)}</>;
}

function rewardLine(r?: { headline?: string; resources?: Record<string, number>; troops?: Record<string, number>; itemRarity?: string | null }) {
  if (!r) return null;
  const res = r.resources ? Object.entries(r.resources).filter(([, v]) => v > 0) : [];
  const tr = r.troops ? Object.entries(r.troops).filter(([, v]) => v > 0) : [];
  return (
    <span className="bt-reward-chips">
      {res.map(([k, v]) => <span key={k} title={titleCase(k)}>{RES_GLYPH[k] ?? "📦"} {v.toLocaleString()}</span>)}
      {tr.map(([k, v]) => <span key={k} title={titleCase(k)}>{glyph(k)} {v}</span>)}
      {r.itemRarity && <span className={"bt-item " + (RARITY_CLASS[r.itemRarity] ?? "")}>💎 {titleCase(r.itemRarity)} item</span>}
    </span>
  );
}

export default function BanditTowerPanel({ active, heroes, onClose, onChanged }: {
  active: CityDetail; heroes: Hero[]; onClose: () => void; onChanged: () => void;
}) {
  const win = useDraggable<HTMLDivElement>();
  const [state, setState] = useState<BanditTowerState | null>(null);
  const [levels, setLevels] = useState<BanditTowerLevelRow[]>([]);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [heroId, setHeroId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [result, setResult] = useState<BanditTowerAttackResult | null>(null);
  const [showRoadmap, setShowRoadmap] = useState(false);

  const load = () => {
    getBanditTower().then(setState).catch(e => setErr(e.message));
    getBanditTowerLevels().then(setLevels).catch(() => {});
  };
  useEffect(load, []);

  const heroesHere = heroes.filter(h => h.unlocked && h.state === "IDLE" && h.stationedCityId === active.id);

  async function attack() {
    const troops = Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
    if (Object.keys(troops).length === 0) { setErr("Select at least one unit"); return; }
    setErr(""); setBusy(true); setResult(null);
    try {
      const r = await attackBanditTower(active.id, troops, heroId);
      setResult(r); setCounts({}); load(); onChanged();
    } catch (e: any) { setErr(e.message); }
    finally { setBusy(false); }
  }

  const full = state?.defendersFull ?? {};
  const remaining = state?.defendersRemaining ?? {};
  const fullTotal = Object.values(full).reduce((a, b) => a + b, 0);
  const remTotal = Object.values(remaining).reduce((a, b) => a + b, 0);
  const cleared = fullTotal - remTotal;
  const pct = fullTotal > 0 ? Math.round((cleared / fullTotal) * 100) : 0;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window bt-window" ref={win} onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>🏰 Bandit Tower {state && <span className="muted">— Level {Math.min(state.currentLevel, state.maxLevel)} / {state.maxLevel}</span>}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          {!state ? <p className="muted">Climbing…</p> : state.complete ? (
            <div className="bt-complete">
              <div className="bt-trophy">🏆</div>
              <h3>You have topped the Bandit Tower.</h3>
              <p className="muted">All 100 levels cleared — the summit is yours.</p>
            </div>
          ) : (
            <>
              <div className="bt-summary">
                <div><span className="muted">Highest cleared</span><b>Lv {state.highestCleared}</b></div>
                {state.nextMilestone && (
                  <div><span className="muted">Next milestone</span><b>Lv {state.nextMilestone.level}</b>
                    <small className="muted"> · {state.nextMilestone.headline}</small></div>
                )}
              </div>

              {/* current level card */}
              <div className={"bt-level-card" + (state.isMilestone ? " milestone" : "")}>
                <div className="bt-level-head">
                  <h3>Level {state.currentLevel}{state.isMilestone ? " ★ Milestone" : ""}</h3>
                  {state.resistedElement && (
                    <span className="bt-resist" title="Defenders resist this element — attack with a different element to fare better">
                      🛡 Resists {ELEMENT_GLYPH[state.resistedElement]} {titleCase(state.resistedElement)}
                    </span>
                  )}
                </div>
                <div className="bt-def-row">
                  <span className="muted">Defenders: {remTotal} / {fullTotal} remaining{cleared > 0 ? " — keep attacking to clear" : ""}</span>
                </div>
                <div className="bt-def-bar"><i style={{ width: pct + "%" }} /></div>
                <div className="bt-troops">{troopChips(remTotal > 0 ? remaining : full)}</div>
                <div className="bt-reward-line"><span className="muted">Reward:</span> {rewardLine(state.reward)}</div>
              </div>

              {/* attack form */}
              <div className="bt-attack">
                <h4>Attack from {active.name} <small className="muted">({active.race?.name ?? "—"} · {active.race?.element ? ELEMENT_GLYPH[active.race.element] : ""})</small></h4>
                {active.units.length === 0 ? (
                  <p className="muted">No troops in this city. Train some, or switch to a city with an army.</p>
                ) : (
                  <>
                    <div className="raid-selectall">
                      <button className="btn ghost tiny" onClick={() => setCounts(Object.fromEntries(active.units.map(u => [u.type, u.count])))}>Select all</button>
                      <button className="btn ghost tiny" onClick={() => setCounts({})}>Clear</button>
                    </div>
                    {active.units.map(u => (
                      <div key={u.type} className="raid-row">
                        <span>{glyph(u.type)} {titleCase(u.type)} <small className="muted">({u.count})</small></span>
                        <input type="number" min={0} max={u.count} value={counts[u.type] || 0}
                          onChange={e => setCounts({ ...counts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
                      </div>
                    ))}
                    <HeroPicker heroes={heroesHere} value={heroId} onChange={setHeroId} />
                    <p className="bt-warn">⚠ Losses are real — troops you send can die. Damage to the defenders persists between attacks.</p>
                    <button className="btn" disabled={busy} onClick={attack}>{busy ? "Resolving…" : "⚔ Attack this level"}</button>
                  </>
                )}
                {err && <p className="bad-text" style={{ marginTop: 6 }}>{err}</p>}
              </div>

              {/* last attack result */}
              {result && (
                <div className={"bt-result " + (result.cleared ? "win" : "loss")}>
                  <h4>{result.cleared ? `✅ Level ${result.level} cleared!` : `Level ${result.level} — defenders repelled the wave`}</h4>
                  <div className="bt-result-grid">
                    <div><span className="muted">Your losses</span> {troopChips(result.troopsLost)}</div>
                    <div><span className="muted">Defenders defeated</span> {troopChips(result.defendersDefeated)}</div>
                    {!result.cleared && <div><span className="muted">Defenders left</span> {troopChips(result.defendersRemaining)}</div>}
                  </div>
                  {result.cleared && result.reward && (
                    <div className="bt-result-reward">
                      🎁 {rewardLine({ resources: result.reward.resources, troops: result.reward.troops, itemRarity: result.reward.item?.rarity ?? null })}
                      {result.reward.item && <span className={"bt-item " + (RARITY_CLASS[result.reward.item.rarity] ?? "")}> · {result.reward.item.name}</span>}
                      {result.heroXp ? <span className="muted"> · +{result.heroXp} hero XP</span> : null}
                    </div>
                  )}
                  {result.towerComplete && <p className="bt-warn">🏆 That was the top floor — the Tower is conquered!</p>}
                </div>
              )}

              {/* roadmap */}
              <button className="btn ghost bt-roadmap-toggle" onClick={() => setShowRoadmap(s => !s)}>
                {showRoadmap ? "▾ Hide roadmap" : "▸ Show the 100-level roadmap"}
              </button>
              {showRoadmap && (
                <div className="bt-roadmap">
                  {levels.map(l => (
                    <div key={l.level} className={"bt-floor st-" + l.status.toLowerCase() + (l.isMilestone ? " ms" : "")}>
                      <span className="bt-floor-n">{l.status === "CLEARED" ? "✓" : l.level}</span>
                      <span className="bt-floor-lvl">Lv {l.level}{l.isMilestone ? " ★" : ""}</span>
                      <span className="bt-floor-el" title={"Resists " + l.resistedElement}>{ELEMENT_GLYPH[l.resistedElement]}</span>
                      <span className="bt-floor-rw">{l.reward.headline}{l.reward.itemRarity ? ` (${titleCase(l.reward.itemRarity)})` : ""}</span>
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
