import { useEffect, useState } from "react";
import {
  getHeroes, setHeroAttributes, armHeroSkill,
  getHeroInventory, equipHeroItem, unequipHeroSlot, getMissions,
} from "../api";
import { useDraggable } from "../useDraggable";
import type { Hero, HeroSkillDto, HeroItemDto, CitySummary } from "../types";

const BUFF_LABEL: Record<string, string> = {
  ATTACK_PCT: "Attack", DEFENSE_PCT: "Defense", DEFENSE_FIRE_PCT: "Fire def", DEFENSE_WIND_PCT: "Wind def",
  DEFENSE_EARTH_PCT: "Earth def", DEFENSE_WATER_PCT: "Water def", TRAVEL_TIME_PCT: "Travel time", NAVAL_TRAVEL_TIME_PCT: "Naval speed",
  LOOT_PCT: "Loot", DROP_CHANCE_PCT: "Drop chance", HERO_XP_PCT: "Hero XP", SKILL_COOLDOWN_PCT: "Skill cooldown",
  WOUND_RECOVERY_PCT: "Wound recovery", LOSS_REDUCTION_PCT: "Losses",
};
const EQUIP_SLOTS = ["weapon", "armor", "relic", "pet"] as const;
const ATTR_INFO: Record<string, { glyph: string; blurb: string; preview: (n: number) => string }> = {
  leadership: { glyph: "👑", blurb: "Your troops fight harder under your command.", preview: n => `+${n * 2}% attack/defense` },
  cunning:    { glyph: "🦊", blurb: "Plunder more and march faster.", preview: n => `+${n * 3}% loot · −${(n * 1.5).toFixed(1)}% travel` },
  valor:      { glyph: "🛡", blurb: "Your soldiers die less and recover faster.", preview: n => `−${n * 2}% losses` },
};
const ATTRS = ["leadership", "cunning", "valor"] as const;
type Attr = typeof ATTRS[number];
const SKILL_DESC: Record<string, string> = {
  CHARGE: "+25% attack power on your next offensive battle.",
  PHALANX: "+30% EARTH defence on the next defence of its city.",
  FORCED_MARCH: "Your next movement ignores 40% of travel time.",
  WAR_CRY: "Your troops take no losses in the first round of the next fight.",
};
const HERO_PORTRAIT: Record<string, string> = { LEO: "🛡", TITANIA: "🧚" };

function countdown(iso: string | null): string {
  if (!iso) return "";
  let s = Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
  const h = Math.floor(s / 3600); s -= h * 3600; const m = Math.floor(s / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m ${s - m * 60}s`;
}

export default function HeroPanel({ cities, onClose, onChanged, focusHeroKey }: {
  cities: CitySummary[]; onClose: () => void; onChanged?: () => void; focusHeroKey?: string;
}) {
  const win = useDraggable<HTMLDivElement>();
  const [heroes, setHeroes] = useState<Hero[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<string>(focusHeroKey ?? "LEO");
  const [starter, setStarter] = useState<{ done: number; total: number }>({ done: 0, total: 7 });

  const load = () => getHeroes().then(hs => setHeroes(hs)).catch(() => setHeroes([])).finally(() => setLoading(false));
  useEffect(() => { load(); getMissions().then(m => setStarter({ done: m.starterDone, total: m.starterTotal })).catch(() => {}); }, []);
  useEffect(() => { if (focusHeroKey) setTab(focusHeroKey); }, [focusHeroKey]);

  const afterChange = (h: Hero) => { setHeroes(hs => hs.map(x => x.id === h.id ? h : x)); onChanged?.(); };
  const active = heroes.find(h => h.heroKey === tab) ?? heroes[0];

  return (
    <div className="mvov-backdrop" onClick={onClose}>
      <div className="mvov hero-panel" ref={win} onClick={e => e.stopPropagation()}>
        <div className="mvov-head">
          <h2>🏛 Heroes</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="hero-tabs">
          {heroes.map(h => (
            <button key={h.heroKey} className={"hero-tab" + (tab === h.heroKey ? " active" : "") + (h.unlocked ? "" : " locked")}
              onClick={() => setTab(h.heroKey)}>
              {HERO_PORTRAIT[h.heroKey]} {h.name}{!h.unlocked && " 🔒"}
            </button>
          ))}
        </div>
        <div className="hero-body">
          {loading ? <p className="muted" style={{ padding: 24 }}>Summoning your heroes…</p>
            : !active ? <p className="muted" style={{ padding: 24 }}>No heroes.</p>
              : !active.unlocked ? <LockedHero hero={active} starter={starter} />
                : <HeroDashboard hero={active} onChanged={afterChange} />}
        </div>
      </div>
    </div>
  );
}

function LockedHero({ hero, starter }: { hero: Hero; starter: { done: number; total: number } }) {
  const pct = Math.round((starter.done / Math.max(1, starter.total)) * 100);
  return (
    <div className="hero-locked">
      <div className="hero-portrait big locked">{HERO_PORTRAIT[hero.heroKey]}</div>
      <h2>{hero.name} the Fairy</h2>
      <p className="muted">Complete the starter missions to recruit {hero.name}, a Fairy hero with stronger
        Cunning (more loot, faster marches) — a swift raider to complement Leo.</p>
      <div className="hero-xpbar"><i style={{ width: pct + "%" }} /><span>{starter.done} / {starter.total} missions complete</span></div>
      <p className="muted" style={{ fontSize: 12 }}>Open the Missions panel to see what's next.</p>
    </div>
  );
}

function Allocator({ pool, value, onChange }: {
  pool: number; value: Record<Attr, number>; onChange: (v: Record<Attr, number>) => void;
}) {
  const spent = ATTRS.reduce((a, k) => a + value[k], 0);
  const left = pool - spent;
  const set = (k: Attr, d: number) => {
    const nv = Math.max(0, value[k] + d);
    if (d > 0 && left <= 0) return;
    onChange({ ...value, [k]: nv });
  };
  return (
    <div className="hero-alloc">
      <div className="hero-alloc-left">Points to assign: <b>{left}</b></div>
      {ATTRS.map(k => (
        <div className="hero-attr-row" key={k}>
          <div className="hero-attr-head">
            <span>{ATTR_INFO[k].glyph} {k.charAt(0).toUpperCase() + k.slice(1)}</span>
            <span className="hero-attr-ctl">
              <button onClick={() => set(k, -1)} disabled={value[k] <= 0}>−</button>
              <b>{value[k]}</b>
              <button onClick={() => set(k, +1)} disabled={left <= 0}>+</button>
            </span>
          </div>
          <div className="muted hero-attr-blurb">{ATTR_INFO[k].blurb}</div>
          {value[k] > 0 && <div className="hero-attr-prev">{ATTR_INFO[k].preview(value[k])}</div>}
        </div>
      ))}
    </div>
  );
}

function HeroDashboard({ hero, onChanged }: {
  hero: Hero; onChanged: (h: Hero) => void;
}) {
  const [alloc, setAlloc] = useState<Record<Attr, number>>({ leadership: 0, cunning: 0, valor: 0 });
  const [err, setErr] = useState("");
  const [pickSlot, setPickSlot] = useState<string | null>(null);
  const [inv, setInv] = useState<HeroItemDto[]>([]);
  const [, force] = useState(0);
  useEffect(() => { const t = setInterval(() => force(x => x + 1), 1000); return () => clearInterval(t); }, []);
  // reload the shared relic inventory whenever this hero changes (equip/unequip updates it)
  useEffect(() => { getHeroInventory().then(setInv).catch(() => setInv([])); }, [hero]);

  const spent = ATTRS.reduce((a, k) => a + alloc[k], 0);
  const xpPct = Math.min(100, Math.round((hero.currentXp / Math.max(1, hero.xpToNextLevel)) * 100));
  const act = async (fn: () => Promise<Hero>) => { setErr(""); try { onChanged(await fn()); setAlloc({ leadership: 0, cunning: 0, valor: 0 }); } catch (e: any) { setErr(e.message); } };

  const stateBadge = hero.state === "IDLE" ? `IDLE — in ${hero.stationedCityName ?? "—"}`
    : hero.state === "MARCHING" ? (hero.stationedCityName ? `MARCHING to ${hero.stationedCityName}` : "MARCHING with an army")
      : hero.state === "SETTLING" ? "FOUNDING a city"
        : `WOUNDED — recovers in ${countdown(hero.woundedUntil)}`;

  return (
    <div className="hero-dash">
      <div className="hero-dash-head">
        <div className="hero-portrait">{HERO_PORTRAIT[hero.heroKey]}</div>
        <div className="hero-dash-id">
          <h2>{hero.name} <small className="muted">· {hero.race.charAt(0) + hero.race.slice(1).toLowerCase()}</small></h2>
          <div className="muted">Level {hero.level}</div>
          <div className="hero-xpbar"><i style={{ width: xpPct + "%" }} /><span>{hero.currentXp} / {hero.xpToNextLevel} XP</span></div>
          <span className={"hero-state st-" + hero.state.toLowerCase()}>{stateBadge}</span>
        </div>
      </div>

      <div className="hero-bonuses">
        <span>👑 +{hero.bonuses.attackPct}% atk</span>
        <span>🛡 +{hero.bonuses.defensePct}% def</span>
        <span>🦊 +{hero.bonuses.lootPct}% loot</span>
        <span>🐢 −{hero.bonuses.travelPct}% travel</span>
        <span>❤ −{hero.bonuses.lossReductionPct}% losses</span>
      </div>

      <div className="br-section-label">Attributes</div>
      <div className="hero-attr-current">
        {ATTRS.map(k => <span key={k}>{ATTR_INFO[k].glyph} {k}: <b>{hero.attributes[k]}</b></span>)}
      </div>
      {hero.unspentAttributePoints > 0 && (
        <>
          <Allocator pool={hero.unspentAttributePoints} value={alloc} onChange={setAlloc} />
          <button className="btn" disabled={spent === 0} onClick={() => act(() => setHeroAttributes(hero.id, alloc.leadership, alloc.cunning, alloc.valor))}>Confirm distribution</button>
        </>
      )}

      <div className="br-section-label">Skills</div>
      <div className="hero-skills">
        {hero.skills.map(s => <SkillCard key={s.id} s={s} disabled={hero.state !== "IDLE" && hero.state !== "MARCHING"}
          onArm={() => act(() => armHeroSkill(hero.id, s.id))} />)}
      </div>

      {hero.specialEffects && hero.specialEffects.length > 0 && (
        <div className="hero-loadout-fx">
          {hero.specialEffects.map((e, i) => <span className="hero-fx-chip" key={i}>✦ {e}</span>)}
        </div>
      )}

      <div className="br-section-label">Equipment</div>
      <div className="hero-equip-slots">
        {EQUIP_SLOTS.map(slot => {
          const it = hero.equipment[slot];
          return <button className={"hero-slot" + (it ? " filled rar-" + it.rarity.toLowerCase() : "")} key={slot}
            title={it ? "Click to change or unequip" : "Click to equip an item"}
            onClick={() => setPickSlot(slot)}>
            <span className="hero-slot-name">{slot}</span>
            <span>{it ? it.name : "— empty —"}</span>
            {it && <small className="muted">{Object.entries(it.buffs).map(([b, v]) => `+${v}% ${BUFF_LABEL[b] ?? b}`).join(", ")}</small>}
            {it?.effectLabels?.map((lbl, i) => <small className="hero-slot-fx" key={i}>✦ {lbl}</small>)}
          </button>;
        })}
      </div>

      {pickSlot && <SlotPicker slot={pickSlot} hero={hero} items={inv} onClose={() => setPickSlot(null)}
        onEquip={(id) => { act(() => equipHeroItem(hero.id, id)); setPickSlot(null); }}
        onUnequip={() => { act(() => unequipHeroSlot(hero.id, pickSlot.toUpperCase())); setPickSlot(null); }} />}

      {err && <div className="hero-inline-err">{err}</div>}
    </div>
  );
}

/** Pick an item for one equipment slot — lists the slot's items, equip/unequip from here. */
function SlotPicker({ slot, hero, items, onEquip, onUnequip, onClose }: {
  slot: string; hero: Hero; items: HeroItemDto[];
  onEquip: (id: number) => void; onUnequip: () => void; onClose: () => void;
}) {
  const equipped = hero.equipment[slot as keyof Hero["equipment"]];
  const choices = items.filter(i => i.slot === slot.toUpperCase() && !i.equipped);
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(460px,100%)" }}>
        <div className="modal-header">
          <h2>{slot.charAt(0).toUpperCase() + slot.slice(1)}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          {equipped && (
            <div className="slot-current">
              <div><b>{equipped.name}</b> <small className="muted">· equipped</small>
                <div><small className="muted">{Object.entries(equipped.buffs).map(([b, v]) => `+${v}% ${BUFF_LABEL[b] ?? b}`).join(", ")}</small></div>
              </div>
              <button className="btn ghost danger" onClick={onUnequip}>Unequip</button>
            </div>
          )}
          <div className="slot-choices">
            {choices.length === 0
              ? <p className="muted">No {slot} items available — defeat island bosses to find more.</p>
              : choices.map(it => (
                <div className={"hero-item rar-" + it.rarity.toLowerCase() + (it.seen ? "" : " unseen")} key={it.id}>
                  <div className="hero-item-head"><b>{it.name}</b>{!it.seen && <span className="hero-item-new">NEW</span>}</div>
                  <small className="muted">{it.rarity}</small>
                  <small>{Object.entries(it.buffs).map(([b, v]) => `+${v}% ${BUFF_LABEL[b] ?? b}`).join(", ")}</small>
                  {it.effectLabels?.map((lbl, i) => <small className="hero-slot-fx" key={i}>✦ {lbl}</small>)}
                  <button className="btn" onClick={() => onEquip(it.id)}>Equip</button>
                </div>
              ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function SkillCard({ s, onArm, disabled }: { s: HeroSkillDto; onArm: () => void; disabled: boolean }) {
  const onCd = s.availableAt != null;
  return (
    <div className={"hero-skill-card" + (s.unlocked ? "" : " locked") + (s.armed ? " armed" : "")}>
      <h4>{s.id.replace("_", " ")}</h4>
      <p className="muted">{SKILL_DESC[s.id]}</p>
      {!s.unlocked ? <span className="muted">🔒 Unlocks at level {s.unlockLevel}</span>
        : s.armed ? <span className="hero-armed">⚔ Armed</span>
          : onCd ? <span className="muted">⏳ {countdown(s.availableAt)}</span>
            : <button className="btn ghost" disabled={disabled} onClick={onArm}>Arm</button>}
    </div>
  );
}
