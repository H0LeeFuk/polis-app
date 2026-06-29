import { useEffect, useMemo, useState } from "react";
import { getHeroInventory } from "../api";
import { useDraggable } from "../useDraggable";
import type { HeroItemDto, ItemSlot, ItemRarity } from "../types";

const BUFF_LABEL: Record<string, string> = {
  ATTACK_PCT: "Attack", DEFENSE_PCT: "Defense", DEFENSE_FIRE_PCT: "Fire def", DEFENSE_WIND_PCT: "Wind def",
  DEFENSE_EARTH_PCT: "Earth def", DEFENSE_WATER_PCT: "Water def", TRAVEL_TIME_PCT: "Travel time", NAVAL_TRAVEL_TIME_PCT: "Naval speed",
  LOOT_PCT: "Loot", DROP_CHANCE_PCT: "Drop chance", HERO_XP_PCT: "Hero XP", SKILL_COOLDOWN_PCT: "Skill cooldown",
  WOUND_RECOVERY_PCT: "Wound recovery", LOSS_REDUCTION_PCT: "Losses",
};
const SLOT_GLYPH: Record<string, string> = { WEAPON: "⚔", ARMOR: "🛡", RELIC: "🏺", PET: "🐾" };
const RARITY_LABEL: Record<string, string> = { COMMON: "Common", RARE: "Rare", EPIC: "Epic", LEGENDARY: "Legendary" };
const SLOTS: ItemSlot[] = ["WEAPON", "ARMOR", "RELIC", "PET"];
const RARITIES: ItemRarity[] = ["COMMON", "RARE", "EPIC", "LEGENDARY"];
const MIN_SLOTS = 24;

/** Relic bag shared by both heroes — Aegean armoury styling. Equip from the Heroes panel. */
export default function InventoryModal({ onClose }: { onClose: () => void }) {
  const win = useDraggable<HTMLDivElement>();
  const [items, setItems] = useState<HeroItemDto[] | null>(null);
  const [slot, setSlot] = useState<"ALL" | ItemSlot>("ALL");
  const [rarity, setRarity] = useState<"ALL" | ItemRarity>("ALL");
  const [selId, setSelId] = useState<number | null>(null);

  useEffect(() => { getHeroInventory().then(setItems).catch(() => setItems([])); }, []);

  const shown = useMemo(
    () => (items ?? []).filter(it => (slot === "ALL" || it.slot === slot) && (rarity === "ALL" || it.rarity === rarity)),
    [items, slot, rarity]);
  const sel = useMemo(() => (items ?? []).find(it => it.id === selId) ?? null, [items, selId]);

  const slotCount = Math.max(MIN_SLOTS, Math.ceil((shown.length + 1) / 8) * 8);
  const empties = Math.max(0, slotCount - shown.length);

  return (
    <div className="mvov-backdrop" onClick={onClose}>
      <div className="inv2" ref={win} onClick={e => e.stopPropagation()}>
        <div className="inv2-frame">
          <div className="inv2-head">
            <span className="inv2-title">⚱ Relic Vault</span>
            <button className="inv2-close" onClick={onClose}>✕</button>
          </div>

          <div className="inv2-toolbar">
            <div className="inv2-tabs">
              <button className={"inv2-tab" + (slot === "ALL" ? " on" : "")} onClick={() => setSlot("ALL")}>All</button>
              {SLOTS.map(s => (
                <button key={s} className={"inv2-tab" + (slot === s ? " on" : "")} onClick={() => setSlot(s)}>
                  {SLOT_GLYPH[s]} <span className="inv2-tab-lbl">{s.charAt(0) + s.slice(1).toLowerCase()}</span>
                </button>
              ))}
            </div>
            <span className="inv2-count">{items ? `${(items ?? []).length} relics` : ""}</span>
          </div>

          <div className="inv2-tabs" style={{ marginBottom: 8 }}>
            <button className={"inv2-tab" + (rarity === "ALL" ? " on" : "")} onClick={() => setRarity("ALL")}>Any rarity</button>
            {RARITIES.map(r => (
              <button key={r} className={"inv2-tab rar-" + r.toLowerCase() + (rarity === r ? " on" : "")} onClick={() => setRarity(r)}>
                {RARITY_LABEL[r]}
              </button>
            ))}
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
                      {sel.equippedOn && <span className="inv2-eq-tag">★ Equipped on {sel.equippedOn}</span>}
                    </div>
                    <div className="inv2-detail-slot">{sel.slot.charAt(0) + sel.slot.slice(1).toLowerCase()} · equip from the Heroes panel</div>
                    <div className="inv2-buffs">
                      {Object.entries(sel.buffs).map(([b, v]) => (
                        <span className="inv2-buff" key={b}>+{v}% {BUFF_LABEL[b] ?? b}</span>
                      ))}
                    </div>
                    {sel.effectLabels?.map((lbl, i) => (
                      <div className="inv2-special" key={i}>✦ {lbl}</div>
                    ))}
                  </div>
                </div>
              ) : null}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
