import { useEffect, useMemo, useState } from "react";
import { getHeroInventory } from "../api";
import type { HeroItemDto } from "../types";

const BUFF_LABEL: Record<string, string> = {
  ATTACK_PCT: "Attack", DEFENSE_SHARP_PCT: "Sharp def", DEFENSE_BLUNT_PCT: "Blunt def",
  DEFENSE_DISTANCE_PCT: "Distance def", TRAVEL_TIME_PCT: "Travel time", LOOT_PCT: "Loot", DROP_CHANCE_PCT: "Drop chance",
};
const SLOT_GLYPH: Record<string, string> = { WEAPON: "⚔", ARMOR: "🛡", AMULET: "📿" };
const RARITY_LABEL: Record<string, string> = { COMMON: "Common", RARE: "Rare", EPIC: "Epic" };
const MIN_SLOTS = 24;   // always render a full grid of empty plinths

/** Relic bag shared by both heroes — Aegean armoury styling. Equip from the Heroes panel. */
export default function InventoryModal({ onClose }: { onClose: () => void }) {
  const [items, setItems] = useState<HeroItemDto[] | null>(null);
  const [filter, setFilter] = useState<"ALL" | "WEAPON" | "ARMOR" | "AMULET">("ALL");
  const [selId, setSelId] = useState<number | null>(null);

  useEffect(() => { getHeroInventory().then(setItems).catch(() => setItems([])); }, []);

  const shown = useMemo(
    () => (items ?? []).filter(it => filter === "ALL" || it.slot === filter),
    [items, filter]);
  const sel = useMemo(() => (items ?? []).find(it => it.id === selId) ?? null, [items, selId]);

  const slotCount = Math.max(MIN_SLOTS, Math.ceil((shown.length + 1) / 8) * 8);
  const empties = Math.max(0, slotCount - shown.length);

  return (
    <div className="mvov-backdrop" onClick={onClose}>
      <div className="inv2" onClick={e => e.stopPropagation()}>
        <div className="inv2-frame">
          <div className="inv2-head">
            <span className="inv2-title">⚱ Relic Vault</span>
            <button className="inv2-close" onClick={onClose}>✕</button>
          </div>

          <div className="inv2-toolbar">
            <div className="inv2-tabs">
              {(["ALL", "WEAPON", "ARMOR", "AMULET"] as const).map(f => (
                <button key={f} className={"inv2-tab" + (filter === f ? " on" : "")} onClick={() => setFilter(f)}>
                  {f === "ALL" ? "All" : <>{SLOT_GLYPH[f]} <span className="inv2-tab-lbl">{f.charAt(0) + f.slice(1).toLowerCase()}</span></>}
                </button>
              ))}
            </div>
            <span className="inv2-count">{items ? `${(items ?? []).length} relics` : ""}</span>
          </div>

          {!items ? <p className="muted inv2-msg">Unsealing the vault…</p> : (
            <>
              <div className="inv2-grid">
                {shown.map(it => (
                  <button key={it.id}
                    className={"inv2-slot filled rar-" + it.rarity.toLowerCase() + (selId === it.id ? " sel" : "") + (it.equipped ? " equipped" : "")}
                    onClick={() => setSelId(selId === it.id ? null : it.id)} title={it.name}>
                    <span className="inv2-ico">{SLOT_GLYPH[it.slot] ?? "✦"}</span>
                    {it.equipped && <span className="inv2-eq">★</span>}
                  </button>
                ))}
                {Array.from({ length: empties }).map((_, i) => (
                  <div className="inv2-slot empty" key={"e" + i}><span className="inv2-meander" /></div>
                ))}
              </div>

              {sel ? (
                <div className={"inv2-detail rar-" + sel.rarity.toLowerCase()}>
                  <div className="inv2-detail-ico">{SLOT_GLYPH[sel.slot] ?? "✦"}</div>
                  <div className="inv2-detail-body">
                    <div className="inv2-detail-name">
                      {sel.name}
                      <span className="inv2-rar-tag">{RARITY_LABEL[sel.rarity] ?? sel.rarity}</span>
                      {sel.equipped && <span className="inv2-eq-tag">★ Equipped</span>}
                    </div>
                    <div className="inv2-detail-slot">{sel.slot.charAt(0) + sel.slot.slice(1).toLowerCase()} relic</div>
                    <div className="inv2-buffs">
                      {Object.entries(sel.buffs).map(([b, v]) => (
                        <span className="inv2-buff" key={b}>+{v}% {BUFF_LABEL[b] ?? b}</span>
                      ))}
                    </div>
                  </div>
                </div>
              ) : (
                <p className="muted inv2-hint">
                  {(items ?? []).length === 0
                    ? "The vault is empty. Hold resource nodes and slay island bosses to claim relics."
                    : "Select a relic to inspect it · equip relics from the Heroes panel."}
                </p>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
