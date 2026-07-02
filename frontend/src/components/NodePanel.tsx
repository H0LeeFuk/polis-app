import { useEffect, useState } from "react";
import { getIslandNodes, getNode, occupyNode, supportNode, attackNode, withdrawNode, getIslandBoss, attackBoss } from "../api";
import type { ResourceNode, UnitDto, Hero, IslandBoss } from "../types";
import { UNIT_GLYPH, HeroPicker } from "../movements";

const RACE_ICON: Record<string, string> = { HUMANS: "🏛", GIANTS: "🗿", FAIRIES: "🧚", NEWTS: "🦎" };
const NODE_GLYPH: Record<string, string> = { SACRED_GROVE: "🌳", MARBLE_QUARRY: "⛏", WHEAT_FIELD: "🌾" };
const RES_GLYPH: Record<string, string> = { WOOD: "🪵", STONE: "🪨", WHEAT: "🌾" };
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const glyph = (t: string) => UNIT_GLYPH[t?.toUpperCase()] ?? "⚔";
const fmtN = (n: number) => Math.round(n).toLocaleString("en-US");
function bossRespawn(iso?: string | null): string {
  if (!iso) return "soon";
  let s = Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
  const h = Math.floor(s / 3600); s -= h * 3600; const m = Math.floor(s / 60);
  return h > 0 ? `in ${h}h ${m}m` : m > 0 ? `in ${m}m` : `in ${s}s`;
}

export function statusTone(n: { status: string; viewerControls?: boolean }) {
  if (n.status === "CONTESTED") return "contested";
  if (n.status === "UNCLAIMED") return "unclaimed";
  return n.viewerControls ? "mine" : "enemy";
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
          {/* boss card — Colossus-style shared HP, rewards split by each player's damage share */}
          {boss?.exists && (() => {
            const max = boss.maxHealth ?? 0, cur = boss.currentHealth ?? 0;
            const pct = max > 0 ? Math.max(0, Math.round(cur / max * 100)) : 0;
            return (
            <div className={"boss-card" + (boss.status === "DEFEATED" ? " defeated" : "")}>
              <div className="boss-head">
                <span className="boss-ico">{RACE_ICON[boss.race ?? "HUMANS"]}👹</span>
                <div>
                  <b>{boss.name}</b> <small className="muted">Lv {boss.level} · T{boss.tier} · weakest vs {boss.attackElement}</small>
                  <div className="muted">{boss.status === "DEFEATED"
                    ? `Defeated — respawns ${bossRespawn(boss.respawnAt)}`
                    : "Shared HP · rewards split by your damage share · sea/flying forces only"}</div>
                </div>
              </div>
              {boss.status === "ACTIVE" && (
                <>
                  <div className="boss-hpbar"><i style={{ width: pct + "%" }} /></div>
                  <div className="boss-hp-label">{fmtN(cur)} / {fmtN(max)} HP
                    {(boss.mySharePct ?? 0) > 0 && <> · your share <b>{boss.mySharePct}%</b></>}</div>
                  <button className="btn" onClick={() => setFightBoss(true)}>⚔ Send fleet / flyers</button>
                </>
              )}
            </div>
            );
          })()}

          <div className="br-section-label">Resource buildings</div>
          {!nodes ? <p className="muted">Surveying…</p>
            : nodes.length === 0 ? <p className="muted">No nodes here.</p>
              : <div className="node-list">
                {nodes.map(n => (
                  <div className={"node-row tone-" + statusTone(n)} key={n.id} onClick={() => setOpen(n)}>
                    <span className="node-ico">{NODE_GLYPH[n.nodeType]}</span>
                    <span className="node-row-main">
                      <b>{n.name}</b>
                      <small className="muted">{n.status === "CONTROLLED"
                        ? `${n.controllingAllianceEmblem ?? ""} held by ${n.controllingAllianceName ?? "?"}`
                        : titleCase(n.status)}</small>
                    </span>
                    <span className="node-row-res">{RES_GLYPH[n.producedResource]} {fmtN(n.maxRatePer10Min)}/10min</span>
                  </div>
                ))}
              </div>}
        </div>
      </div>
      {open && <NodePanel nodeId={open.id} ctx={ctx} onClose={() => setOpen(null)} onChanged={load} />}
      {fightBoss && boss?.exists && (
        <BossFightModal boss={boss} ctx={ctx}
          onClose={() => setFightBoss(false)} onDone={() => { setFightBoss(false); load(); ctx.onChanged(); }} />
      )}
    </div>
  );
}

function BossFightModal({ boss, ctx, onClose, onDone }: {
  boss: IslandBoss; ctx: NodeCtx; onClose: () => void; onDone: () => void;
}) {
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState("");
  const selected = Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
  const hasTroops = Object.keys(selected).length > 0;
  const max = boss.maxHealth ?? 0, cur = boss.currentHealth ?? 0;
  const pct = max > 0 ? Math.max(0, Math.round(cur / max * 100)) : 0;

  const send = async () => {
    setBusy(true); ctx.setErr("");
    try {
      const r = await attackBoss(boss.islandId!, ctx.activeCityId, selected, null);
      setNote(`Fleet dispatched — arrives in ~${Math.max(1, Math.round((r.travelSeconds ?? 0) / 60))}m. Damage posts on arrival; check Battle Reports.`);
    } catch (e: any) { ctx.setErr(e.message); } finally { setBusy(false); }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(480px,100%)" }}>
        <div className="modal-header"><h2>⚔ {boss.name}</h2><button className="modal-close" onClick={onClose}>✕</button></div>
        <div className="modal-body"><div className="popup-panel">
          <div className="boss-hpbar"><i style={{ width: pct + "%" }} /></div>
          <div className="boss-hp-label">{fmtN(cur)} / {fmtN(max)} HP · reward pool {fmtN(boss.rewardPoolPerResource ?? 0)} each 🪵🪨🌾 split by damage share</div>

          {note ? (
            <div className="boss-result win"><h3>🚩 Dispatched</h3><p className="muted">{note}</p>
              <button className="btn" onClick={onDone}>Continue</button></div>
          ) : ctx.myUnits.length === 0 ? (
            <p className="muted">No troops in your active city. Train fleet or flying units first.</p>
          ) : (
            <>
              <p className="muted">Send <b>sea or flying</b> forces from your active city (land troops can't reach the boss):</p>
              {ctx.myUnits.map(u => (
                <div key={u.type} className="raid-row">
                  <span>{glyph(u.type)} {titleCase(u.type)} <small className="muted">({u.count})</small></span>
                  <input type="number" min={0} max={u.count} value={counts[u.type] || 0}
                    onChange={e => setCounts({ ...counts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
                </div>
              ))}
              <button className="btn" disabled={busy || !hasTroops} onClick={send}>⚔ Dispatch strike</button>
            </>
          )}

          {(boss.leaderboard?.length ?? 0) > 0 && (
            <div className="boss-lb">
              <div className="br-section-label">Damage leaderboard</div>
              {boss.leaderboard!.map(row => (
                <div className="boss-lb-row" key={row.playerId}>
                  <span>#{row.rank} {row.playerName}</span>
                  <span className="muted">{fmtN(row.damage)} · {row.sharePct}%</span>
                </div>
              ))}
            </div>
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

  const load = () => getNode(nodeId).then(setNode).catch(() => {});
  useEffect(() => { load(); }, [nodeId]);

  if (!node) return null;
  const selected = Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
  const hasTroops = Object.keys(selected).length > 0;
  const iHaveTroops = node.myPop > 0;

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
            <p className="muted" style={{ marginTop: 0 }}>Contested control point — <b>allies reinforce &amp; share</b> the payout; <b>enemies attack to seize</b> it.</p>
            <div className="node-detail-grid">
              <div><strong>Status</strong><span className={"node-badge tone-" + statusTone(node)}>{titleCase(node.status)}</span></div>
              <div><strong>Controller</strong><span>{node.controllingAllianceName
                ? <>{node.controllingAllianceEmblem} {node.controllingAllianceName}</> : "Unclaimed"}</span></div>
              <div><strong>Produces</strong><span>{RES_GLYPH[node.producedResource]} {fmtN(node.maxRatePer10Min)}/10min
                {node.status === "CONTROLLED" && node.garrisonPop < node.garrisonCap && <small className="muted"> · now {fmtN(node.ratePer10Min)}</small>}</span></div>
              <div><strong>Garrison</strong><span>{node.garrisonPop} / {node.garrisonCap} pop</span></div>
            </div>
            {node.status === "CONTROLLED" && (
              <div className="node-accum">💰 Pays every 10 min to controllers' cities, split by troop share
                {node.mySharePct > 0 && <> · <b>your share {node.mySharePct}%</b></>}</div>
            )}
            {/* per-player holders */}
            <div className="node-holders">
              {node.holders.length === 0 ? <span className="muted">No troops guarding.</span>
                : node.holders.map(h => (
                  <div className="node-holder" key={h.playerId}>
                    <span>{h.playerName}{h.playerId === ctx.myPlayerId ? " (you)" : ""}</span>
                    <span className="muted">{Object.entries(h.troops).filter(([, q]) => q > 0).map(([t, q]) => `${glyph(t)}${q}`).join(" ")} · {h.sharePct}%</span>
                  </div>
                ))}
            </div>

            {/* actions: occupy (unclaimed) · support+withdraw (allied) · attack (enemy) */}
            {node.status !== "CONTROLLED" ? (
              <>
                <TroopSelector myUnits={ctx.myUnits} counts={counts} setCounts={setCounts} label="Occupy with troops:" />
                <HeroPicker heroes={ctx.heroes} value={heroId} onChange={setHeroId} />
                <button className="btn" disabled={busy || !hasTroops} onClick={() => run(() => occupyNode(node.id, body()))}>🚩 Occupy</button>
              </>
            ) : node.viewerControls ? (
              <>
                <TroopSelector myUnits={ctx.myUnits} counts={counts} setCounts={setCounts} label="Support (reinforce &amp; share):" />
                <div className="node-actions">
                  <button className="btn" disabled={busy || !hasTroops} onClick={() => run(() => supportNode(node.id, body()))}>➕ Support</button>
                  {iHaveTroops && <button className="btn ghost" disabled={busy} onClick={() => run(() => withdrawNode(node.id))}>↩ Withdraw mine</button>}
                </div>
              </>
            ) : (
              <>
                <TroopSelector myUnits={ctx.myUnits} counts={counts} setCounts={setCounts} label="Attack to seize:" />
                <HeroPicker heroes={ctx.heroes} value={heroId} onChange={setHeroId} />
                <button className="btn" disabled={busy || !hasTroops} onClick={() => run(() => attackNode(node.id, body()))}>⚔ Attack &amp; seize</button>
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
  const allSelected = myUnits.every(u => (counts[u.type] || 0) >= u.count);
  const selectAll = () => setCounts({ ...counts, ...Object.fromEntries(myUnits.map(u => [u.type, u.count])) });
  const clearAll = () => setCounts({ ...counts, ...Object.fromEntries(myUnits.map(u => [u.type, 0])) });
  return (
    <>
      <p className="muted node-sel-head">{label}
        <button className="node-selectall" onClick={allSelected ? clearAll : selectAll}>
          {allSelected ? "Clear" : "Select all"}</button>
      </p>
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
