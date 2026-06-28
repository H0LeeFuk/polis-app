import { useEffect, useState } from "react";
import { getIslandNodes, getNode, occupyNode, reinforceNode, attackNode, withdrawNode, getIslandBoss, attackBoss } from "../api";
import type { ResourceNode, UnitDto, Hero, IslandBoss, BossAttackResult } from "../types";
import { UNIT_GLYPH, HeroPicker } from "../movements";

const RACE_ICON: Record<string, string> = { HUMANS: "🏛", GIANTS: "🗿", FAIRIES: "🧚", NEWTS: "🦎" };
const NODE_GLYPH: Record<string, string> = { SACRED_GROVE: "🌳", MARBLE_QUARRY: "⛏", SILVER_VEIN: "⛰" };
const RES_GLYPH: Record<string, string> = { WOOD: "🪵", STONE: "🪨", SILVER: "🪙" };
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const glyph = (t: string) => UNIT_GLYPH[t?.toUpperCase()] ?? "⚔";

export function statusTone(n: { status: string; controllingPlayerId: number | null }, myId: number) {
  if (n.status === "CONTESTED") return "contested";
  if (n.status === "UNCLAIMED") return "unclaimed";
  return n.controllingPlayerId === myId ? "mine" : "enemy";
}

/** Modal listing the nodes on a resource island; clicking one opens the node detail. */
export function ResourceIslandModal({ islandId, islandName, ctx, onClose }: {
  islandId: number; islandName: string; ctx: NodeCtx; onClose: () => void;
}) {
  const [nodes, setNodes] = useState<ResourceNode[] | null>(null);
  const [open, setOpen] = useState<ResourceNode | null>(null);
  const [boss, setBoss] = useState<IslandBoss | null>(null);
  const [fightBoss, setFightBoss] = useState(false);
  const load = () => {
    getIslandNodes(islandId).then(setNodes).catch(() => setNodes([]));
    getIslandBoss(islandId).then(setBoss).catch(() => setBoss(null));
  };
  useEffect(() => { load(); }, [islandId]);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(520px,100%)" }}>
        <div className="modal-header"><h2>🏝 {islandName}</h2><button className="modal-close" onClick={onClose}>✕</button></div>
        <div className="modal-body">
          {/* boss card */}
          {boss?.exists && (
            <div className={"boss-card" + (boss.status === "DEFEATED" ? " defeated" : "")}>
              <div className="boss-head">
                <span className="boss-ico">{RACE_ICON[boss.race ?? "HUMANS"]}👹</span>
                <div>
                  <b>{boss.name}</b> <small className="muted">Lv {boss.level} · {boss.race}</small>
                  <div className="muted">{boss.status === "DEFEATED" ? "Defeated — regrouping" : "Guards this island · drops a rare relic"}</div>
                </div>
              </div>
              {boss.status === "ACTIVE" && (
                <>
                  <div className="boss-troops">{Object.entries(boss.defenderTroops ?? {}).map(([t, q]) => <span key={t}>{glyph(t)} {q}</span>)}</div>
                  <button className="btn" onClick={() => setFightBoss(true)}>⚔ Attack boss</button>
                </>
              )}
            </div>
          )}

          <div className="br-section-label">Resource buildings</div>
          {!nodes ? <p className="muted">Surveying…</p>
            : nodes.length === 0 ? <p className="muted">No nodes here.</p>
              : <div className="node-list">
                {nodes.map(n => (
                  <div className={"node-row tone-" + statusTone(n, ctx.myPlayerId)} key={n.id} onClick={() => setOpen(n)}>
                    <span className="node-ico">{NODE_GLYPH[n.nodeType]}</span>
                    <span className="node-row-main">
                      <b>{n.name}</b>
                      <small className="muted">{n.status === "CONTROLLED" ? `held by ${n.controllingPlayerName}` : titleCase(n.status)}</small>
                    </span>
                    <span className="node-row-res">{RES_GLYPH[n.producedResource]} {n.ratePerHour}/h</span>
                  </div>
                ))}
              </div>}
        </div>
      </div>
      {open && <NodePanel nodeId={open.id} ctx={ctx} onClose={() => setOpen(null)} onChanged={load} />}
      {fightBoss && boss?.exists && (
        <BossFightModal islandId={islandId} bossName={boss.name ?? "Boss"} ctx={ctx}
          onClose={() => setFightBoss(false)} onDone={() => { setFightBoss(false); load(); ctx.onChanged(); }} />
      )}
    </div>
  );
}

function BossFightModal({ islandId, bossName, ctx, onClose, onDone }: {
  islandId: number; bossName: string; ctx: NodeCtx; onClose: () => void; onDone: () => void;
}) {
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [heroId, setHeroId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<BossAttackResult | null>(null);
  const selected = Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
  const hasTroops = Object.keys(selected).length > 0;

  const send = async () => {
    setBusy(true); ctx.setErr("");
    try { setResult(await attackBoss(islandId, ctx.activeCityId, selected, heroId)); }
    catch (e: any) { ctx.setErr(e.message); } finally { setBusy(false); }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(460px,100%)" }}>
        <div className="modal-header"><h2>⚔ {bossName}</h2><button className="modal-close" onClick={onClose}>✕</button></div>
        <div className="modal-body"><div className="popup-panel">
          {result ? (
            <div className={"boss-result " + (result.outcome === "WIN" ? "win" : "loss")}>
              <h3>{result.outcome === "WIN" ? "Victory!" : "Defeated"}</h3>
              {result.reward && (
                <p>Loot: {result.reward.wood}🪵 {result.reward.stone}🪨 {result.reward.silver}🪙
                  {result.reward.relic && <> · <b>{result.reward.relic.rarity} {result.reward.relic.name}</b> 🎁</>}
                  {result.heroXp ? ` · +${result.heroXp} hero XP` : ""}</p>
              )}
              {result.outcome === "LOSS" && <p className="muted">Your army was beaten back. Train more troops or bring a hero.</p>}
              <button className="btn" onClick={onDone}>Continue</button>
            </div>
          ) : ctx.myUnits.length === 0 ? (
            <p className="muted">No troops in your active city. Train some first.</p>
          ) : (
            <>
              <p className="muted">Strike from your active city:</p>
              {ctx.myUnits.map(u => (
                <div key={u.type} className="raid-row">
                  <span>{titleCase(u.type)} <small className="muted">({u.count})</small></span>
                  <input type="number" min={0} max={u.count} value={counts[u.type] || 0}
                    onChange={e => setCounts({ ...counts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
                </div>
              ))}
              <HeroPicker heroes={ctx.heroes} value={heroId} onChange={setHeroId} />
              <button className="btn" disabled={busy || !hasTroops} onClick={send}>⚔ Attack</button>
            </>
          )}
        </div></div>
      </div>
    </div>
  );
}

export interface NodeCtx {
  myPlayerId: number;
  activeCityId: number;
  myUnits: UnitDto[];
  heroes: Hero[];                // idle unlocked heroes in the active city
  setErr: (s: string) => void;
  onChanged: () => void;
}

export default function NodePanel({ nodeId, ctx, onClose, onChanged }: {
  nodeId: number; ctx: NodeCtx; onClose: () => void; onChanged?: () => void;
}) {
  const [node, setNode] = useState<ResourceNode | null>(null);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [heroId, setHeroId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [accum, setAccum] = useState(0);

  const load = () => getNode(nodeId).then(n => { setNode(n); setAccum(n.accumulated); }).catch(() => {});
  useEffect(() => { load(); }, [nodeId]);
  // live-tick the accumulated counter client-side
  useEffect(() => {
    if (!node || node.status !== "CONTROLLED") return;
    const t = setInterval(() => setAccum(a => a + node.ratePerHour / 3600), 1000);
    return () => clearInterval(t);
  }, [node?.id, node?.status, node?.ratePerHour]);

  if (!node) return null;
  const mine = node.controllingPlayerId === ctx.myPlayerId;
  const selected = Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
  const hasTroops = Object.keys(selected).length > 0;

  const run = async (fn: () => Promise<any>) => {
    setBusy(true); ctx.setErr("");
    try { await fn(); setCounts({}); await load(); onChanged?.(); ctx.onChanged(); }
    catch (e: any) { ctx.setErr(e.message); }
    finally { setBusy(false); }
  };
  const body = () => ({ cityId: ctx.activeCityId, troops: selected, heroId });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(480px,100%)" }}>
        <div className="modal-header">
          <h2>{NODE_GLYPH[node.nodeType]} {node.name}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="popup-panel">
            <div className="node-detail-grid">
              <div><strong>Status</strong><span className={"node-badge tone-" + statusTone(node, ctx.myPlayerId)}>{titleCase(node.status)}</span></div>
              <div><strong>Controller</strong><span>{node.controllingPlayerName ?? "Unclaimed"}</span></div>
              <div><strong>Produces</strong><span>{RES_GLYPH[node.producedResource]} {node.ratePerHour}/h</span></div>
              <div><strong>Garrison</strong><span>{node.garrisonPop} / {node.garrisonCap} pop</span></div>
            </div>
            {node.status === "CONTROLLED" && (
              <div className="node-accum">📦 Accumulated: <b>{Math.floor(accum)}</b> {titleCase(node.producedResource)}
                {node.controllingAllianceName && <small className="muted"> · delivers to {node.controllingAllianceName} treasury</small>}</div>
            )}
            <div className="node-garrison">
              {Object.entries(node.garrison).filter(([, q]) => q > 0).map(([t, q]) => <span key={t}>{glyph(t)} {q}</span>)}
              {Object.keys(node.garrison).length === 0 && <span className="muted">No troops guarding.</span>}
            </div>

            {/* actions */}
            {mine ? (
              <>
                <TroopSelector myUnits={ctx.myUnits} counts={counts} setCounts={setCounts} label="Reinforce from active city:" />
                <div className="node-actions">
                  <button className="btn" disabled={busy || !hasTroops} onClick={() => run(() => reinforceNode(node.id, body()))}>➕ Reinforce</button>
                  <button className="btn ghost" disabled={busy} onClick={() => run(() => withdrawNode(node.id))}>↩ Withdraw all</button>
                </div>
              </>
            ) : node.status === "CONTROLLED" ? (
              <>
                <TroopSelector myUnits={ctx.myUnits} counts={counts} setCounts={setCounts} label="Attack with troops:" />
                <HeroPicker heroes={ctx.heroes} value={heroId} onChange={setHeroId} />
                <button className="btn" disabled={busy || !hasTroops} onClick={() => run(() => attackNode(node.id, body()))}>⚔ Attack node</button>
              </>
            ) : (
              <>
                <TroopSelector myUnits={ctx.myUnits} counts={counts} setCounts={setCounts} label="Occupy with troops:" />
                <HeroPicker heroes={ctx.heroes} value={heroId} onChange={setHeroId} />
                <button className="btn" disabled={busy || !hasTroops} onClick={() => run(() => occupyNode(node.id, body()))}>🚩 Occupy node</button>
              </>
            )}
          </div>
        </div>
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
