import { useEffect, useState } from "react";
import { settleSlot, foundCity, settlePreview } from "../api";
import type { Hero, AttackPreview, RaceId, RaceInfo, FoundingStatus } from "../types";
import { fmtArrival, fmtEta } from "../movements";

/** Client mirror of the seeded races — drives the race cards (name, icon, traits, roster). */
export const RACES: { id: RaceId; name: string; icon: string; blurb: string; traits: string[]; roster: string }[] = [
  { id: "HUMANS", name: "Humans", icon: "🏛",
    blurb: "Balanced and adaptable settlers of the Aegean.",
    traits: [],
    roster: "Hoplite · Swordsman · Archer · Horseman · Trireme" },
  { id: "GIANTS", name: "Giants", icon: "🗿",
    blurb: "Towering brutes who raise cities of stone.",
    traits: [],
    roster: "Boulder Thrower · Troll · Stone Giant · Colossus · War Barge" },
  { id: "FAIRIES", name: "Fairies", icon: "🧚",
    blurb: "Swift and graceful folk of the glades.",
    traits: [],
    roster: "Sprite · Pixie Archer · Glimmer Guard · Moth Rider · Dragonfly Skiff" },
  { id: "NEWTS", name: "Newts", icon: "🦎",
    blurb: "Amphibious raiders at home on the open sea.",
    traits: [],
    roster: "Mudling · Newt Spear · Snapper · Tide Raider · Leviathan" },
];

const fmtDuration = (s: number) => {
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  if (h > 0) return `${h}h ${m.toString().padStart(2, "0")}m`;
  if (m > 0) return `${m}m ${sec.toString().padStart(2, "0")}s`;
  return `${sec}s`;
};

// ---- compact race chip (city header) ----

export function RaceBadge({ race }: { race: RaceInfo }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="race-badge" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <button className="race-chip" onClick={() => setOpen(o => !o)} title={race.name}>
        <span className="race-ico">{race.icon}</span> {race.name}
      </button>
      {open && (
        <div className="race-pop">
          <div className="race-pop-head">{race.icon} {race.name}</div>
          <p className="muted">{race.description}</p>
        </div>
      )}
    </div>
  );
}

// ---- founding status banner (global) ----

export function FoundingBanner({ founding, now, onChoose }: {
  founding: NonNullable<FoundingStatus["founding"]>; now: number; onChoose: () => void;
}) {
  const awaiting = founding.phase === "AWAITING_RACE";
  return (
    <div className={"founding-banner" + (awaiting ? " ready" : "")}>
      <span className="fb-ico">🏛</span>
      {awaiting ? (
        <>
          <div className="fb-text">
            <span className="fb-title">Your hero has reached {founding.islandName}</span>
            <span className="fb-sub">Choose a race to found your first city and begin your reign.</span>
          </div>
          <div className="fb-races">
            {RACES.map(r => <span key={r.id} className="fb-race" title={r.name}>{r.icon}</span>)}
          </div>
          <button className="btn" onClick={onChoose}>Choose a race →</button>
        </>
      ) : (
        <div className="fb-text">
          <span className="fb-sub">Hero marching to found a city on <b>{founding.islandName}</b>
            {founding.arriveAt && <> — arrives in <b>{fmtEta(founding.arriveAt, now)}</b></>}.</span>
        </div>
      )}
    </div>
  );
}

// ---- found-city stepper modal ----

export function FoundCityModal({ islandId, islandName, slotIndex, heroes, fromCityId, fromCityName,
  startAtRace, onClose, onChanged, setErr }: {
  islandId: number; islandName: string; slotIndex: number;
  heroes: Hero[];
  fromCityId: number | null; fromCityName: string | null;
  startAtRace?: boolean;
  onClose: () => void; onChanged: () => void; setErr: (s: string) => void;
}) {
  const [step, setStep] = useState<"send" | "race">(startAtRace ? "race" : "send");
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<AttackPreview | null>(null);

  // which hero to send (only unlocked + idle + stationed somewhere)
  const eligible = heroes.filter(h => h.unlocked && h.state === "IDLE" && h.stationedCityId != null);
  const [heroId, setHeroId] = useState<number | null>(eligible[0]?.id ?? null);
  const sendHero = eligible.find(h => h.id === heroId) ?? null;

  // race step state
  const [race, setRace] = useState<RaceId | null>(null);
  const [name, setName] = useState("");

  const heroReady = eligible.length > 0 && sendHero != null;
  const origin = startAtRace ? fromCityId : (sendHero?.stationedCityId ?? null);
  const originName = startAtRace ? (fromCityName ?? "origin city") : (sendHero?.stationedCityName ?? "—");

  useEffect(() => {
    if (step !== "send" || origin == null || heroId == null) return;
    setPreview(null);
    settlePreview(islandId, origin, heroId).then(setPreview).catch(() => setPreview(null));
  }, [step, islandId, origin, heroId]);

  async function send() {
    if (origin == null || heroId == null) return;
    setErr(""); setBusy(true);
    try { await settleSlot(islandId, slotIndex, origin, heroId); onChanged(); onClose(); }
    catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  }
  async function found() {
    if (!race) { setErr("Pick a race for your new city"); return; }
    setErr(""); setBusy(true);
    try {
      const res = await foundCity(islandId, slotIndex, race, name.trim(), null);   // hero stays in the new city
      if (res.ok === false) setErr(res.message ?? "That slot was taken — your hero is returning home");
      onChanged(); onClose();   // refresh either way: clears the founding banner, shows the hero marching home
    } catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(720px,100%)" }}>
        <div className="modal-header">
          <h2>Found a city — {islandName} · plot {slotIndex + 1}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="found-steps">
            <span className={"found-step" + (step === "send" ? " active" : "") + (step === "race" ? " done" : "")}>1 · Send the hero</span>
            <span className="found-step-sep">→</span>
            <span className={"found-step" + (step === "race" ? " active" : "")}>2 · Choose a race</span>
          </div>

          {step === "send" ? (
            <div className="popup-panel">
              <p className="muted">Founding a city sends a <b>Hero</b> to this empty plot. The hero is
                unavailable for the whole trip and until you pick a race on arrival.</p>
              {!heroReady ? (
                <p className="found-warn">No idle hero is stationed and available. Station Leo or Titania
                  in a city first (Heroes panel), or wait for a marching hero to return.</p>
              ) : (
                <>
                  <div className="found-hero-select">
                    <span className="hp-label">Send which hero?</span>
                    <div className="hp-opts">
                      {eligible.map(h => (
                        <button type="button" key={h.id} className={"hp-chip" + (heroId === h.id ? " sel" : "")}
                          onClick={() => setHeroId(h.id)}>
                          {h.heroKey === "TITANIA" ? "🧚" : "🛡"} {h.name} <small>Lv{h.level}</small>
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className="popup-grid">
                    <div><strong>Departs from</strong><span>{originName}</span></div>
                  </div>
                  {preview ? (
                    <div className="travel-card">
                      <div className="travel-row"><span>📏 Distance</span><b>{preview.distance} tiles</b></div>
                      <div className="travel-row"><span>⏱ Travel time</span><b>{fmtDuration(preview.travelSeconds)}</b></div>
                      <div className="travel-row"><span>🕐 Arrives</span><b>{fmtArrival(preview.arriveAt)}</b></div>
                    </div>
                  ) : <div className="travel-card"><div className="travel-row muted">Charting the route…</div></div>}
                  <button className="btn" disabled={busy || origin == null} onClick={send}>🏛 Send hero to settle</button>
                </>
              )}
            </div>
          ) : (
            <div className="popup-panel">
              <p className="muted">Your hero waits at the plot. Choose a race — this is <b>permanent</b> for the new city.</p>
              <div className="race-cards">
                {RACES.map(rc => (
                  <button key={rc.id} className={"race-card" + (race === rc.id ? " sel" : "")} onClick={() => setRace(rc.id)}>
                    <div className="race-card-head"><span className="race-card-ico">{rc.icon}</span> {rc.name}</div>
                    <p className="race-card-blurb">{rc.blurb}</p>
                    <ul className="race-card-traits">{rc.traits.map(t => <li key={t}>{t}</li>)}</ul>
                    <div className="race-card-roster"><small className="muted">Units: {rc.roster}</small></div>
                  </button>
                ))}
              </div>
              <label className="found-field">
                <span>City name</span>
                <input value={name} maxLength={40} placeholder="New colony name" onChange={e => setName(e.target.value)} />
              </label>
              <button className="btn" disabled={busy || !race} onClick={found}>🏛 Found city</button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
