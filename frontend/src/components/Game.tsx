import { useEffect, useRef, useState } from "react";
import {
  getState, doBuild, doTrain, doCancel, doRename, doFinish, getInbox, getMyMovements,
  getUnreadReportCount, doAttack, getHeroes, getFoundingStatus, getMissions, getTradeDeliveries, assignHero,
  createAlliance, getServerTime, getMyAlliance, inviteToAlliance, acceptAllianceInvite, declineAllianceInvite, postAllianceForum,
  deassignHero, getAltar, runFestival, callCityGuard,
} from "../api";
import type { GameState, CityDetail, PlayerDto, InboxMsg, PlayerMovements, UnitDto, Hero, FoundingStatus, Trainable, ShipRole, AltarState, AllianceView, BuildingDto } from "../types";
import { FoundingBanner, FoundCityModal, RaceBadge, RACES } from "./FoundCity";
import MissionsPanel from "./MissionsPanel";
import InventoryModal from "./InventoryModal";
import { EndgamePanel } from "./WondersPanel";
import LibraryPanel from "./LibraryPanel";
import TradePanel from "./TradePanel";
import { buildingSvg, constructionSvg, emptyPlotSvg } from "../buildings";
import { PLACEMENTS, PLACEMENT_BY_TYPE, TERRAIN_URL, SCENE_W, SCENE_H, ICON_BASE, type Placement } from "../cityScene";
import SpyPanel from "./SpyPanel";
import WorldView from "./WorldView";
import Rankings from "./Rankings";
import MovementsOverview from "./MovementsOverview";
import BattleReports from "./BattleReports";
import HeroPanel from "./HeroPanel";
import BanditTowerPanel from "./BanditTowerPanel";
import ProfilePanel from "./ProfilePanel";
import SiegePanel, { RaceChoiceModal } from "./SiegePanel";
import TroopDetailPanel from "./TroopDetailPanel";
import SimulatorPanel from "./SimulatorPanel";
import { useDraggable } from "../useDraggable";
import { CityMovementsPanel, TravelPreview, UnitTooltip, HeroPicker, HERO_GLYPH, UNIT_GLYPH } from "../movements";
import leoImg from "../assets/leo.png";
import titaniaImg from "../assets/titania.png";

const fmt = (n: number) => n >= 10000 ? (n / 1000).toFixed(n >= 100000 ? 0 : 1) + "k" : Math.floor(n).toString();
const RES_GLYPH: Record<string, string> = {
  wood: "🪵", stone: "🪨", wheat: "🌾",
  coal: "⬛", crystals: "💎", iron: "⛓", pearls: "🫧",
};
/** Lowercased resource key for a ResourceType id (WOOD -> wood, COAL -> coal). */
const resKey = (id: string) => id.toLowerCase();
const ELEMENT_GLYPH: Record<string, string> = { FIRE: "🔥", WIND: "🌬", EARTH: "🌍", WATER: "💧" };

// hero portrait art, keyed by heroKey; accent/glow drive badges, status, XP fill, silhouette
const HERO_IMG: Record<string, string> = { LEO: leoImg, TITANIA: titaniaImg };
const HERO_ACCENT: Record<string, { accent: string; glow: string }> = {
  LEO: { accent: "#e0995e", glow: "#e8584a" },
  TITANIA: { accent: "#6fae4f", glow: "#8fe6b0" },
};
const heroSilhouette = (accent: string, glow: string, name: string) => {
  const s = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 312 460'>
    <defs><radialGradient id='g' cx='50%' cy='30%' r='60%'>
      <stop offset='0%' stop-color='${accent}' stop-opacity='0.32'/>
      <stop offset='100%' stop-color='${accent}' stop-opacity='0'/></radialGradient></defs>
    <rect width='312' height='460' fill='#0d0907'/>
    <rect width='312' height='460' fill='url(#g)'/>
    <g fill='${accent}' fill-opacity='0.5'>
      <circle cx='156' cy='128' r='54'/>
      <path d='M52 300 Q52 214 156 214 Q260 214 260 300 Z'/></g>
    <text x='156' y='250' text-anchor='middle' font-family='Inter, Arial, sans-serif'
      font-size='12' font-weight='700' letter-spacing='1.4'
      fill='#c2b1a6' fill-opacity='0.85'>DROP ${name.toUpperCase()} ART</text>
  </svg>`;
  return "data:image/svg+xml," + encodeURIComponent(s);
};
// preload both portraits once so tab switches don't flash
[leoImg, titaniaImg].forEach(src => { const im = new Image(); im.src = src; });
const MOVE_BADGE: Record<string, string> = { LAND: "🚶", FLYING: "🕊", SWIMMING: "🌊" };
const MOVE_LABEL: Record<string, string> = {
  LAND: "Land — needs a transport ship to cross open water",
  FLYING: "Flying — crosses water freely",
  SWIMMING: "Swimming — crosses water freely, slower on land",
};

const ResIcon = ({ kind }: { kind: string }) => {
  const i: Record<string, React.ReactNode> = {
    wood: <g><rect x="3" y="13" width="18" height="6" rx="3" fill="#8a5a2b" stroke="#5e3a17" strokeWidth="1.2" /><ellipse cx="6" cy="16" rx="2" ry="3" fill="#c98f4e" stroke="#5e3a17" strokeWidth="1.2" /><rect x="4" y="6" width="16" height="6" rx="3" fill="#9c6a36" stroke="#5e3a17" strokeWidth="1.2" /><ellipse cx="6.5" cy="9" rx="2" ry="3" fill="#d6a05c" stroke="#5e3a17" strokeWidth="1.2" /></g>,
    stone: <g><path d="M5 18 L3 11 L8 6 L15 6 L21 12 L19 18 Z" fill="#9aa3ad" stroke="#5b6570" strokeWidth="1.3" strokeLinejoin="round" /><path d="M8 6 L11 12 L19 18 M3 11 L11 12 L5 18" fill="none" stroke="#6b7681" strokeWidth="1" /></g>,
    wheat: <g><path d="M12 3 L12 21" stroke="#b8860b" strokeWidth="1.4" /><path d="M12 6 q4 -1 5 -4 q-4 0 -5 4 M12 6 q-4 -1 -5 -4 q4 0 5 4 M12 11 q4 -1 5 -4 q-4 0 -5 4 M12 11 q-4 -1 -5 -4 q4 0 5 4" fill="#e6c64a" stroke="#b8860b" strokeWidth="0.8" strokeLinejoin="round" /></g>,
    pop: <g><circle cx="9" cy="8" r="3.4" fill="#e6c98f" stroke="#9c7a3e" strokeWidth="1.2" /><path d="M3 20 c0-4 3-6.5 6-6.5 s6 2.5 6 6.5" fill="#cf9d52" stroke="#9c7a3e" strokeWidth="1.2" /><circle cx="16" cy="9" r="2.8" fill="#d9b878" stroke="#9c7a3e" strokeWidth="1.2" /><path d="M13.5 20 c0-3.3 2-5.5 4.5-5.5 s4 2 4 5.5" fill="#bd8c46" stroke="#9c7a3e" strokeWidth="1.2" /></g>,
    gold: <g><circle cx="12" cy="12" r="8.5" fill="#f2c94c" stroke="#b8860b" strokeWidth="1.6" /><circle cx="12" cy="12" r="5.5" fill="none" stroke="#d9a520" strokeWidth="1.2" /><text x="12" y="16" fontSize="9" textAnchor="middle" fill="#9a6b08" fontWeight="700">★</text></g>,
  };
  // special resources (coal/crystals/iron/pearls) fall back to their emoji glyph
  if (!i[kind]) return <span className="resicon-emoji" title={kind}>{RES_GLYPH[kind] || "📦"}</span>;
  return <svg className="resicon" viewBox="0 0 24 24" width="22" height="22">{i[kind]}</svg>;
};

function remaining(finishAt: string | null, now: number) {
  if (!finishAt) return null;
  return Math.max(0, Math.round((new Date(finishAt).getTime() - now) / 1000));
}
const clock = (s: number) => {
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  return (h ? `${h}:` : "") + `${m.toString().padStart(h ? 2 : 1, "0")}:${sec.toString().padStart(2, "0")}`;
};

export default function Game({ onLogout }: { onLogout: () => void }) {
  const [state, setState] = useState<GameState | null>(null);
  const [activeCityId, setActiveCityId] = useState<number | undefined>(undefined);
  const [tab, setTab] = useState<"city" | "world">("city");
  const [modal, setModal] = useState<"rankings" | "profile" | "alliance" | "messages" | "endgame" | "sieges" | null>(null);
  const [err, setErr] = useState("");
  const [note, setNote] = useState("");
  const [now, setNow] = useState(Date.now());
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [editing, setEditing] = useState(false);
  const [nameDraft, setNameDraft] = useState("");
  const [moves, setMoves] = useState<PlayerMovements | null>(null);
  const [showMoves, setShowMoves] = useState(false);
  const [showTroopDetail, setShowTroopDetail] = useState(false);
  const [showSimulator, setShowSimulator] = useState(false);
  const [showReports, setShowReports] = useState(false);
  const [unreadReports, setUnreadReports] = useState(0);
  const [raidAgain, setRaidAgain] = useState<{ id: number; name: string } | null>(null);
  const [showHero, setShowHero] = useState(false);
  const [showTower, setShowTower] = useState(false);
  // bumped whenever an order that creates a movement is dispatched, to force the movements panel to reload now
  const [movesNonce, setMovesNonce] = useState(0);
  const bumpMoves = () => setMovesNonce(n => n + 1);
  const [heroes, setHeroes] = useState<Hero[]>([]);
  const [founding, setFounding] = useState<FoundingStatus["founding"]>(null);
  const [chooseRace, setChooseRace] = useState(false);
  const [showMissions, setShowMissions] = useState(false);
  const [showInv, setShowInv] = useState(false);
  const [claimable, setClaimable] = useState(0);
  const [activeMission, setActiveMission] = useState<string | null>(null);
  const [nudgeDismissed, setNudgeDismissed] = useState(false);
  const polling = useRef<number>();
  // Monotonic refresh sequence: getState calls from the 3s poll, a rush/cancel action, and city
  // switches run concurrently and resolve out of order. Without this guard a slow poll that captured
  // the PRE-rush queue can resolve AFTER the rush's refresh and overwrite it with stale data — the
  // rushed job "reappears" and the queue looks inflated until the next poll heals it. Only the latest
  // issued request is allowed to apply its result.
  const refreshSeq = useRef(0);

  const refreshUnreadReports = () => getUnreadReportCount().then(r => setUnreadReports(r.count)).catch(() => {});
  const refreshHeroes = () => getHeroes().then(setHeroes).catch(() => {});
  const refreshFounding = () => getFoundingStatus().then(s => setFounding(s.founding)).catch(() => {});
  const refreshMissions = () => getMissions().then(m => {
    setClaimable(m.missions.filter(x => x.status === "COMPLETED").length);
    const first = m.missions.find(x => x.status === "ACTIVE");
    setActiveMission(first ? `${first.title} — ${first.description}` : null);
  }).catch(() => {});

  useEffect(() => { if (!err) return; const t = setTimeout(() => setErr(""), 3500); return () => clearTimeout(t); }, [err]);
  useEffect(() => { if (!note) return; const t = setTimeout(() => setNote(""), 4500); return () => clearTimeout(t); }, [note]);

  // trade convoy arrivals: surface a light delivery notification + refresh the destination's resources
  useEffect(() => {
    const f = () => getTradeDeliveries().then(d => {
      if (!d.length) return;
      const msg = d.map(cv => {
        const cargo = Object.entries(cv.cargo).filter(([, v]) => v > 0)
          .map(([k, v]) => `${Math.round(v).toLocaleString()} ${k.charAt(0) + k.slice(1).toLowerCase()}`).join(", ");
        return `🚚 Delivered ${cargo} to ${cv.destination}`;
      }).join(" · ");
      setNote(msg); refresh();
    }).catch(() => {});
    f();
    const t = window.setInterval(f, 15000);
    return () => clearInterval(t);
  }, []);

  async function refresh(cityId = activeCityId) {
    const seq = ++refreshSeq.current;
    try {
      const s = await getState(cityId);
      if (seq !== refreshSeq.current) return;   // a newer refresh already won — drop this stale result
      setState(s);
      if (cityId === undefined) setActiveCityId(s.active.id);
    } catch (e: any) {
      const m = String(e.message);
      // A failure during the very first load means a dead session (e.g. a stale token from a
      // previous database). Drop to the login screen instead of hanging on "Loading…".
      if (m.includes("401") || m.includes("403") || state === null) onLogout();
      else setErr(m);
    }
  }

  useEffect(() => { refresh(); }, []);
  useEffect(() => {
    polling.current = window.setInterval(() => refresh(), 3000);
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => { clearInterval(polling.current); clearInterval(t); };
  }, [activeCityId]);

  // empire-wide movement feed: drives the nav badge and the global overview
  useEffect(() => {
    const f = () => getMyMovements().then(setMoves).catch(() => {});
    f();
    const t = window.setInterval(f, 15000);
    return () => clearInterval(t);
  }, []);

  // unread battle-report badge: poll every 60s per spec
  useEffect(() => {
    refreshUnreadReports();
    const t = window.setInterval(refreshUnreadReports, 60000);
    return () => clearInterval(t);
  }, []);

  // heroes drive the action hero-pickers + nav badge
  useEffect(() => {
    refreshHeroes();
    const t = window.setInterval(refreshHeroes, 30000);
    return () => clearInterval(t);
  }, []);

  // founding status drives the banner + the race-choice prompt on arrival
  useEffect(() => {
    refreshFounding();
    const t = window.setInterval(refreshFounding, 10000);
    return () => clearInterval(t);
  }, []);

  // mission claim badge
  useEffect(() => {
    refreshMissions();
    const t = window.setInterval(refreshMissions, 30000);
    return () => clearInterval(t);
  }, []);

  if (!state) return <div className="app"><p className="muted">Loading the Aegean…</p></div>;
  const { player, cities, active } = state;

  const action = (fn: () => Promise<any>) => async () => {
    setErr("");
    try { await fn(); await refresh(); } catch (e: any) { setErr(e.message); }
  };
  const switchCity = (id: number) => { setEditing(false); setActiveCityId(id); refresh(id); };
  const goToCity = (id: number | null) => {
    if (id == null) return;
    if (cities.some(c => c.id === id)) { setShowMoves(false); setTab("city"); switchCity(id); }
  };
  const moveCount = moves?.movements.length ?? 0;
  const hostileInbound = (moves?.summary.incomingThreats ?? 0) > 0;
  const heroesHere = heroes.filter(h => h.unlocked && h.state === "IDLE" && h.stationedCityId === active.id);
  const commitRename = () => {
    const nm = nameDraft.trim();
    setEditing(false);
    if (nm && nm !== active.name) action(() => doRename(active.id, nm))();
  };

  return (
    <div className="app">
      <div className="topbar">
        <div className="topbar-left">
          <div className="brand">POLIS</div>
          <div className="cityswitch">
            {editing ? (
              <input className="cs-edit" autoFocus value={nameDraft} maxLength={40}
                onChange={e => setNameDraft(e.target.value)}
                onKeyDown={e => { if (e.key === "Enter") commitRename(); if (e.key === "Escape") setEditing(false); }}
                onBlur={commitRename} />
            ) : cities.length > 1 ? (
              <select className="cs-select" value={active.id} onChange={e => switchCity(Number(e.target.value))}>
                {cities.map(c => <option key={c.id} value={c.id}>{c.capital ? "★ " : ""}{c.name}</option>)}
              </select>
            ) : <span className="cs-name solo">{active.name}</span>}
            {!editing && <button className="cs-rename" title="Rename city"
              onClick={() => { setNameDraft(active.name); setEditing(true); }}>✎</button>}
          </div>
          {active.race && <RaceBadge race={active.race} />}
        </div>

        <ResourceBar active={active} gold={player.gold} />

        <div className="topbar-right">
          <PlayerCrest player={player} onClick={() => setModal("profile")} />
          <button className="logout" onClick={onLogout}>log out</button>
        </div>
      </div>

      {err && <div className="toast" onClick={() => setErr("")}>{err}</div>}
      {note && <div className="toast note" onClick={() => setNote("")}>{note}</div>}

      {founding && <FoundingBanner founding={founding} now={now}
        onChoose={() => { setTab("world"); setChooseRace(true); }} />}

      {!nudgeDismissed && activeMission && (
        <div className="quest-bar">
          <span className="quest-flag">🏳</span>
          <span className="quest-label">Next Quest</span>
          <span className="quest-text">{activeMission}</span>
          <button className="quest-link" onClick={() => setShowMissions(true)}>View missions</button>
          <button className="quest-x" onClick={() => setNudgeDismissed(true)}>✕</button>
        </div>
      )}

      <div className="game-shell">
        <SideNav
          unreadReports={unreadReports} claimable={claimable}
          heroPts={heroes.reduce((a, h) => a + (h.unlocked ? h.unspentAttributePoints : 0), 0)}
          serverLine={`${player.ownedCities} ${player.ownedCities === 1 ? "city" : "cities"} · ${fmt(player.totalPoints)} pts`}
          onReports={() => setShowReports(true)} onMissions={() => setShowMissions(true)}
          onHeroes={() => setShowHero(true)} onInventory={() => setShowInv(true)}
          onTower={() => setShowTower(true)}
          onSimulator={() => setShowSimulator(true)}
          onModal={setModal} />

        <div className="stage">
          <div className="stage-bar">
            <div className="map-tabs">
              <button className="map-tab active" onClick={() => setTab(tab === "city" ? "world" : "city")}>
                {tab === "city" ? "🌐 World View" : "🏠 City View"}
              </button>
            </div>
          </div>
          <div className="stage-body">
            {tab === "city" && <CityTab key={active.id} active={active} now={now} counts={counts} setCounts={setCounts}
              onBuild={(t) => action(() => doBuild(active.id, t))()}
              onTrain={(t, c) => action(() => doTrain(active.id, t, c))()}
              onCancel={(j) => action(() => doCancel(active.id, j))()}
              onFinish={(j) => action(() => doFinish(active.id, j))()}
              onFound={() => setTab("world")}
              onCallGuard={action(() => callCityGuard(active.id))} />}

            {tab === "world" && <WorldView activeCityId={active.id} myUnits={active.units} heroes={heroes} myPlayerId={player.id} onChanged={() => { refresh(); refreshHeroes(); bumpMoves(); }} setErr={setErr} />}
          </div>
        </div>

        {(tab === "city" || tab === "world") && (
          <aside className="side-info">
            <TroopsPanel units={active.units} trainable={active.trainable} onDetails={() => setShowTroopDetail(true)} />
            <CityMovementsPanel cityId={active.id} now={now} reloadKey={movesNonce}
              onExpand={() => setShowMoves(true)}
              onAttackAgain={(id, name) => setRaidAgain({ id, name })} />
          </aside>
        )}

        {/* hero card floats bottom-right of the stage */}
        <HeroSummary heroes={heroes} cityId={active.id} now={now} onOpen={() => setShowHero(true)}
          onChanged={refreshHeroes} setErr={setErr} />
      </div>

      {showMoves && <MovementsOverview data={moves} now={now} onClose={() => setShowMoves(false)} onGoCity={goToCity} />}
      {showTroopDetail && <TroopDetailPanel cityId={active.id} cityName={active.name}
        onClose={() => setShowTroopDetail(false)} onChanged={() => { refresh(); bumpMoves(); }} />}
      {showSimulator && <SimulatorPanel onClose={() => setShowSimulator(false)} />}

      {showReports && <BattleReports cities={cities} unreadCount={unreadReports}
        onClose={() => setShowReports(false)} onUnreadChange={refreshUnreadReports}
        onAttackAgain={(id, name) => { setShowReports(false); setRaidAgain({ id, name }); }} />}

      {showHero && <HeroPanel cities={cities} onClose={() => setShowHero(false)} onChanged={refreshHeroes} />}
      {showTower && <BanditTowerPanel active={active} heroes={heroes} onClose={() => setShowTower(false)}
        onChanged={() => { refresh(); refreshHeroes(); }} />}

      {showMissions && <MissionsPanel onClose={() => setShowMissions(false)}
        onChanged={() => { refreshMissions(); refreshHeroes(); }} />}

      {showInv && <InventoryModal onClose={() => setShowInv(false)} />}

      {chooseRace && founding && founding.phase === "AWAITING_RACE" && (
        <FoundCityModal islandId={founding.islandId} islandName={founding.islandName} slotIndex={founding.slotIndex}
          heroes={heroes} fromCityId={founding.fromCityId}
          fromCityName={cities.find(c => c.id === founding.fromCityId)?.name ?? null}
          startAtRace onClose={() => setChooseRace(false)} setErr={setErr}
          onChanged={() => { setChooseRace(false); refresh(); refreshHeroes(); refreshFounding(); }} />
      )}

      {raidAgain && <RaidAgainModal originCityId={active.id} originName={active.name} myUnits={active.units}
        heroes={heroesHere} target={raidAgain} onClose={() => setRaidAgain(null)}
        onSend={async (units, heroId, intent) => {
          setErr("");
          try { await doAttack(active.id, raidAgain.id, units, heroId, intent); setRaidAgain(null); await refresh(); refreshHeroes(); bumpMoves(); }
          catch (e: any) { setErr(e.message); }
        }} />}

      {modal === "sieges" && <SiegePanel originCityId={active.id} originName={active.name}
        myUnits={active.units} heroes={heroes} onClose={() => setModal(null)}
        onChanged={() => { refresh(); refreshHeroes(); bumpMoves(); }} />}

      {active.conqueredPendingRace && <RaceChoiceModal cityId={active.id} cityName={active.name}
        onChosen={() => refresh()} />}

      {modal === "rankings" && <Modal title="Rankings" onClose={() => setModal(null)}><Rankings /></Modal>}
      {modal === "profile" && <ProfilePanel player={player} cities={cities}
        faction={active.race?.name ?? "—"} onClose={() => setModal(null)} />}
      {modal === "alliance" && <Modal title="Alliance" onClose={() => setModal(null)}>
        <AlliancePanel player={player} onChanged={() => refresh()} setErr={setErr} />
      </Modal>}
      {modal === "messages" && <Modal title="Messages" onClose={() => setModal(null)}><Inbox /></Modal>}
      {modal === "endgame" && <Modal title="Endgame · Wonders of the Aegean" onClose={() => setModal(null)}>
        <EndgamePanel ctx={{ myPlayerId: player.id, activeCityId: active.id, myUnits: active.units, heroes, setErr, onChanged: () => refresh() }} />
      </Modal>}
    </div>
  );
}

const ELEMENT_NAME: Record<string, string> = { FIRE: "Fire", WIND: "Wind", EARTH: "Earth", WATER: "Water" };

function TroopsPanel({ units, trainable, onDetails }: { units: { type: string; count: number }[]; trainable: Trainable[]; onDetails: () => void }) {
  const meta = (type: string) => trainable.find(x => x.type === type);
  const role = (type: string) => {
    const t = meta(type);
    if (!t) return "";
    return t.siege ? "Siege" : (t.attackElement ? ELEMENT_NAME[t.attackElement] : "");
  };
  // Ships/naval are trained at the Harbor (kind SEA); ground troops at the Barracks.
  const isShip = (type: string) => { const t = meta(type); return t?.from === "HARBOR" || t?.kind === "SEA"; };
  const ships = units.filter(u => isShip(u.type));
  const troops = units.filter(u => !isShip(u.type));
  const sum = (arr: { count: number }[]) => arr.reduce((a, u) => a + u.count, 0);

  // Compact square tile: icon + count badge. Full stats go in a native multi-line title so the
  // tooltip is never clipped by the panel's scroll/overflow (a positioned card would be cut off).
  const tile = (u: { type: string; count: number }) => {
    const t = meta(u.type);
    const title = t
      ? `${titleCase(u.type)} — ${role(u.type) || "—"}\n`
        + `⚔ ${t.atk}    🔥 ${t.defFire}  🌬 ${t.defWind}  🌍 ${t.defEarth}  💧 ${t.defWater}\n`
        + `🐢 ${t.speed} min/tile · 👥 ${t.pop} pop${t.carry ? ` · 🎒 ${t.carry}` : ""}`
      : titleCase(u.type);
    return (
      <span className="tp-tile" title={title} key={u.type}>
        <span className="tp-tile-ico">{UNIT_GLYPH[u.type] ?? "⚔"}</span>
        <span className="tp-tile-count">{u.count}</span>
      </span>
    );
  };

  return (
    <div className="side-card troops-panel">
      <div className="sc-head">
        <span className="sc-title">⚔ Troops</span>
        <span className="sc-sub">{sum(troops)} in city · <button className="tp-details" title="Troops abroad & foreign troops here" onClick={onDetails}>🔍 Details</button></span>
      </div>
      {troops.length === 0
        ? <p className="muted tp-empty">No ground troops. Train them in the Barracks.</p>
        : <div className="tp-grid">{troops.map(tile)}</div>}
      <div className="sc-head" style={{ marginTop: 12 }}><span className="sc-title">⛵ Ships</span><span className="sc-sub">{sum(ships)} in port</span></div>
      {ships.length === 0
        ? <p className="muted tp-empty">No ships. Build them at the Harbor.</p>
        : <div className="tp-grid">{ships.map(tile)}</div>}
    </div>
  );
}

function SideNav({ unreadReports, claimable, heroPts, serverLine, onReports, onMissions, onHeroes, onInventory, onTower, onSimulator, onModal }: {
  unreadReports: number; claimable: number; heroPts: number; serverLine: string;
  onReports: () => void; onMissions: () => void; onHeroes: () => void; onInventory: () => void; onTower: () => void; onSimulator: () => void;
  onModal: (m: "rankings" | "profile" | "alliance" | "messages" | "endgame" | "sieges") => void;
}) {
  const Item = ({ icon, label, onClick, badge, hostile }: { icon: string; label: string; onClick: () => void; badge?: number; hostile?: boolean }) => (
    <button className="nav-item" onClick={onClick}>
      <span className="nav-ico">{icon}</span><span className="nav-lbl">{label}</span>
      {badge ? <span className={"nav-pip" + (hostile ? " hostile" : "")}>{badge}</span> : null}
    </button>
  );
  return (
    <nav className="side-nav">
      <ServerClock />
      <div className="nav-group">Command</div>
      <Item icon="📜" label="Reports" onClick={onReports} badge={unreadReports} hostile />
      <Item icon="🏳" label="Missions" onClick={onMissions} badge={claimable} />
      <Item icon="🛡" label="Heroes" onClick={onHeroes} badge={heroPts} />
      <Item icon="🏰" label="Bandit Tower" onClick={onTower} />
      <Item icon="🎒" label="Inventory" onClick={onInventory} />
      <div className="nav-group">Social</div>
      <Item icon="🤝" label="Alliance" onClick={() => onModal("alliance")} />
      <Item icon="✉" label="Messages" onClick={() => onModal("messages")} />
      <Item icon="🏆" label="Rankings" onClick={() => onModal("rankings")} />
      <Item icon="⚑" label="Sieges" onClick={() => onModal("sieges")} />
      <Item icon="⚔" label="Endgame" onClick={() => onModal("endgame")} />
      <Item icon="⚔📜" label="Simulator" onClick={onSimulator} />
      <div className="nav-foot">{serverLine}</div>
    </nav>
  );
}

function ServerClock() {
  const [offset, setOffset] = useState<number | null>(null);
  const [, tick] = useState(0);
  useEffect(() => { getServerTime().then(t => setOffset(t.epochMillis - Date.now())).catch(() => setOffset(0)); }, []);
  useEffect(() => { const id = setInterval(() => tick(x => x + 1), 1000); return () => clearInterval(id); }, []);
  if (offset === null) return null;
  const d = new Date(Date.now() + offset);
  const day = d.toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" });
  const time = d.toLocaleTimeString([], { hour12: false });
  return (
    <div className="server-clock" title="Server time">
      <span className="sc-clock-day">🕐 {day}</span>
      <span className="sc-clock-time">{time}</span>
    </div>
  );
}

function HeroSummary({ heroes, cityId, now, onOpen, onChanged, setErr }: {
  heroes: Hero[]; cityId: number; now: number; onOpen: () => void; onChanged: () => void; setErr: (s: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [selId, setSelId] = useState<number | null>(null);
  const unlocked = heroes.filter(h => h.unlocked);
  // prefer a hero already here, else an unassigned hero ready to assign, else any idle, else first
  const auto = unlocked.find(h => h.stationedCityId === cityId)
    ?? unlocked.find(h => h.stationedCityId == null && h.state === "IDLE")
    ?? unlocked.find(h => h.state === "IDLE") ?? unlocked[0] ?? heroes[0] ?? null;
  // a tab selection overrides the auto-pick — and may point at a still-locked hero (Titania
  // before the missions are done), which renders a locked card below
  const hero = heroes.find(h => h.id === selId) ?? auto;
  // tabs list every hero (locked ones flagged) so Titania is always visible
  const tabs = heroes.length > 1 && (
    <div className="hf-tabs">
      {heroes.map(h => (
        <button key={h.id} className={"hf-tab" + (h.id === hero?.id ? " on" : "") + (h.unlocked ? "" : " locked")}
          onClick={() => setSelId(h.id)}>
          {HERO_GLYPH(h)} {h.name}{h.unlocked ? "" : " 🔒"}
        </button>
      ))}
    </div>
  );
  if (!hero) {
    return (
      <div className="hero-float empty">
        <div className="sc-head"><span className="sc-title">🛡 Hero</span></div>
        <p className="muted">No hero yet. Complete missions to recruit your champion.</p>
        <button className="btn ghost" onClick={onOpen}>View heroes</button>
      </div>
    );
  }
  const xpPct = hero.xpToNextLevel > 0 ? Math.min(100, Math.round((hero.currentXp / hero.xpToNextLevel) * 100)) : 100;
  const arriving = hero.state === "MARCHING" && hero.woundedUntil
    ? Math.max(0, Math.round((new Date(hero.woundedUntil).getTime() - now) / 1000)) : null;
  const idle = hero.state === "IDLE";
  const hereIdle = hero.stationedCityId === cityId && idle;
  const unassigned = hero.stationedCityId == null;
  const elsewhereIdle = !unassigned && hero.stationedCityId !== cityId && idle;
  // status line (coloured): where the hero is and what it's doing
  const status = hero.state === "MARCHING"
    ? { text: arriving != null ? `Marching to ${hero.stationedCityName ?? "city"}` : "Marching with army", cls: "go" }
    : hero.state === "WOUNDED" ? { text: "Wounded", cls: "bad" }
    : hero.stationedCityName ? { text: `Stationed in ${hero.stationedCityName}`, cls: "go" }
    : { text: "Unassigned", cls: "muted" };
  // element from race (Humans FIRE · Giants EARTH · Fairies WIND · Newts WATER)
  const RACE_ELEMENT: Record<string, string> = { HUMANS: "FIRE", GIANTS: "EARTH", FAIRIES: "WIND", NEWTS: "WATER" };
  const element = RACE_ELEMENT[hero.race] ?? "FIRE";
  const a = hero.attributes;
  // display power rollup from level + attributes (cosmetic summary of the hero's strength)
  const pwr = Math.round(40 + hero.level * 8 + a.valor * 6);
  const def = Math.round(25 + hero.level * 6 + a.leadership * 6);
  const spd = Math.round(30 + a.cunning * 5);
  const skillName = hero.armedSkill ?? hero.skills?.find(s => s.armed)?.id ?? hero.skills?.find(s => s.unlocked)?.id ?? null;
  const skillArmed = !!hero.armedSkill || !!hero.skills?.some(s => s.armed);
  const theme = HERO_ACCENT[hero.heroKey] ?? { accent: "#e0995e", glow: "#e8584a" };
  const portrait = HERO_IMG[hero.heroKey] ?? heroSilhouette(theme.accent, theme.glow, hero.name);

  // Locked hero (e.g. Titania before all missions are claimed): same card frame, dimmed art,
  // no stats/deploy — just a recruit hint that points to the missions/hero panel.
  if (!hero.unlocked) {
    return (
      <div className={"hero-float hero-art-" + element.toLowerCase()}>
        {tabs}
        <div className="hf-card hf-locked">
          <img className="hf-art" src={portrait} alt={hero.name} />
          <div className="hf-scrim-top" />
          <div className="hf-scrim" />
          <div className="hf-badges">
            <span className="hf-elem" style={{ borderColor: theme.accent, color: theme.accent }}>{ELEMENT_GLYPH[element]} {element}</span>
            <span className="hf-lv hf-lock-badge">🔒</span>
          </div>
          <div className="hf-overlay">
            <div className="hf-name">{hero.name}</div>
            <div className="hf-sub">{titleCase(hero.race)} champion · <span className="hf-status muted">Locked</span></div>
            <p className="hf-locked-hint">Complete all starter missions to recruit {hero.name}.</p>
            <button className="btn ghost hf-action" onClick={onOpen}>View heroes</button>
          </div>
        </div>
      </div>
    );
  }

  const run = (fn: () => Promise<any>) => async () => {
    setErr(""); setBusy(true);
    try { await fn(); onChanged(); } catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };

  return (
    <div className={"hero-float hero-art-" + element.toLowerCase()}>
      {tabs}
      <div className="hf-card">
        <img className="hf-art" src={portrait} alt={hero.name} />
        <div className="hf-scrim-top" />
        <div className="hf-scrim" />
        <div className="hf-badges">
          <span className="hf-elem" style={{ borderColor: theme.accent, color: theme.accent }}>{ELEMENT_GLYPH[element]} {element}</span>
          <span className="hf-lv"><small>LV</small>{hero.level}</span>
        </div>
        <div className="hf-overlay">
          <div className="hf-name">{hero.name}</div>
          <div className="hf-sub">{titleCase(hero.race)} champion · <span className="hf-status" style={{ color: theme.accent }}>{status.text}</span></div>
          <div className="hf-stats">
            <span>🗡 <b>{pwr}</b> <small>PWR</small></span>
            <span>🛡 <b>{def}</b> <small>DEF</small></span>
            <span>⚡ <b>{spd}</b> <small>SPD</small></span>
          </div>
          {skillName && (
            <div className="hf-skill">
              <span className="hf-skill-name">✦ {titleCase(skillName)}</span>
              <span className="hf-skill-tag">{skillArmed ? "ACTIVE" : "READY"}</span>
            </div>
          )}
          <div className="hf-xp-row"><span>Experience</span><span>{xpPct}%</span>{hero.unspentAttributePoints > 0 && <span className="nav-pip">{hero.unspentAttributePoints}</span>}</div>
          <div className="hf-xp"><i style={{ width: xpPct + "%", background: theme.accent }} /></div>
          {arriving != null
            ? <div className="hf-assigning">
                <button className="btn hf-action" disabled>🕐 Arriving at {hero.stationedCityName ?? "city"} in {clock(arriving)}</button>
                <button className="hf-cancel-x" title="Cancel assignment" disabled={busy} onClick={run(() => deassignHero(hero.id))}>✕</button>
              </div>
            : hereIdle
              ? <button className="btn ghost hf-action" disabled={busy} onClick={run(() => deassignHero(hero.id))}>Deassign from city</button>
              : unassigned && idle
                ? <button className="btn hf-action" disabled={busy} onClick={run(() => assignHero(hero.id, cityId))}>⚔ Deploy hero</button>
                : elsewhereIdle
                  ? <button className="btn ghost hf-action" disabled={busy} onClick={run(() => deassignHero(hero.id))}>Deassign from {hero.stationedCityName}</button>
                  : <button className="btn hf-action" disabled>Hero busy</button>}
        </div>
      </div>
    </div>
  );
}

function AltarSection({ cityId, now }: { cityId: number; now: number }) {
  const [t, setT] = useState<AltarState | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setLocalErr] = useState("");
  const load = () => getAltar(cityId).then(setT).catch(() => {});
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [cityId]);
  if (!t) return <div className="bld-section"><h3>⛧ Rituals</h3><p className="muted">Loading…</p></div>;
  const pr = t.progression;
  const run = (ft: string, fuel: string) => async () => {
    setLocalErr(""); setBusy(true);
    try { await runFestival(cityId, ft, fuel); await load(); } catch (e: any) { setLocalErr(e.message); } finally { setBusy(false); }
  };
  const runningOf = (ft: string) => t.running.find(r => r.festivalType === ft);
  const FestivalOpt = ({ ft, fuel, title, costLine, canAfford, noFundsLabel }: {
    ft: string; fuel: string; title: string; costLine: React.ReactNode; canAfford: boolean; noFundsLabel: string;
  }) => {
    const r = runningOf(ft);
    if (r) {
      const rem = Math.max(0, Math.round((new Date(r.completesAt).getTime() - now) / 1000));
      const pct = Math.min(100, Math.round((1 - rem / Math.max(1, t.durationSeconds)) * 100));
      return (
        <div className="festival-opt">
          <h4>{title}</h4>
          <div className="festival-running">
            <div>⛧ completes in {clock(rem)} → +{r.culturePointsReward} Influence</div>
            <div className="bar"><i style={{ width: pct + "%" }} /></div>
          </div>
        </div>
      );
    }
    return (
      <div className="festival-opt">
        <h4>{title}</h4>
        {costLine}
        <div className="muted">+{t.cultureReward} Influence · ⏱ {clock(t.durationSeconds)}</div>
        <button className="btn" disabled={busy || !canAfford} onClick={run(ft, fuel)}>
          {canAfford ? "Perform rite" : noFundsLabel}</button>
      </div>
    );
  };
  return (
    <div className="bld-section">
      <h3>⛧ Rituals <small className="muted" style={{ marginLeft: 6 }}>⚔ {t.combatPoints} Combat Points</small></h3>
      {err && <div className="hero-inline-err">{err}</div>}
      <div className="altar-prog">
        <div className="altar-prog-row"><b>Level {pr.level} / {pr.maxLevel}</b><span className="muted">Cities {pr.citiesOwned} / {pr.maxCities}</span></div>
        {pr.cultureForNextLevel != null ? (
          <>
            <div className="hf-xp"><i style={{ width: Math.min(100, Math.round((pr.culturePoints / pr.cultureForNextLevel) * 100)) + "%" }} /></div>
            <small className="muted">{pr.culturePoints} / {pr.cultureForNextLevel} Influence to Level {pr.level + 1} → unlocks city #{pr.level + 1}</small>
          </>
        ) : <small className="muted">Maximum level reached — 20 cities unlocked.</small>}
      </div>
      <p className="muted altar-flavor">Offer tribute or the spoils of war at the Altar. Each rite yields <b>1 Influence</b> — accrue enough to reach the next level and unlock another city.</p>
      <div className="festival-menu">
        <FestivalOpt ft="FESTIVAL_OF_PLENTY" fuel="RESOURCES" title="🕯 Rite of Offering"
          costLine={<div className="muted">Cost: {t.resourceCost.toLocaleString()} each 🪵 🪨 🌾</div>}
          canAfford={t.canAffordResources} noFundsLabel="Not enough resources" />
        <FestivalOpt ft="FESTIVAL_OF_TRIUMPH" fuel="COMBAT_POINTS" title="🩸 Rite of Blood"
          costLine={<div className="muted">Cost: {t.combatCost} Combat Points</div>}
          canAfford={t.canAffordCombat} noFundsLabel="Not enough Combat Points" />
      </div>
    </div>
  );
}

function Inbox() {
  const [msgs, setMsgs] = useState<InboxMsg[] | null>(null);
  useEffect(() => { getInbox().then(setMsgs).catch(() => setMsgs([])); }, []);
  if (!msgs) return <p className="muted">Loading…</p>;
  if (msgs.length === 0) return <div className="popup-panel"><p className="muted">No messages yet. Other players can message you from the world map.</p></div>;
  return (
    <div className="popup-panel">
      {msgs.map(m => (
        <div className="msg-row" key={m.id}>
          <div className="msg-head"><strong>{m.from}</strong><small className="muted">{new Date(m.sentAt).toLocaleString()}</small></div>
          <div>{m.body}</div>
        </div>
      ))}
    </div>
  );
}

function AlliancePanel({ onChanged, setErr }: { player: PlayerDto; onChanged: () => void; setErr: (s: string) => void }) {
  const [view, setView] = useState<AllianceView | null>(null);
  const [creating, setCreating] = useState(false);
  const [tag, setTag] = useState("");
  const [name, setName] = useState("");
  const [invitee, setInvitee] = useState("");
  const [post, setPost] = useState("");
  const [busy, setBusy] = useState(false);

  const load = () => getMyAlliance().then(setView).catch(e => setErr(e.message));
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);
  const act = async (fn: () => Promise<any>) => { setErr(""); setBusy(true); try { await fn(); await load(); onChanged(); } catch (e: any) { setErr(e.message); } finally { setBusy(false); } };

  if (!view) return <div className="popup-panel"><p className="muted">Loading…</p></div>;

  // --- not in an alliance: create + pending invites ---
  if (!view.inAlliance) {
    return (
      <div className="popup-panel">
        <h3>No alliance</h3>
        {(view.invites && view.invites.length > 0) && (
          <div className="ally-invites">
            <strong className="ally-sub">Invitations</strong>
            {view.invites.map(inv => (
              <div className="ally-invite" key={inv.allianceId}>
                <span>[{inv.tag}] <b>{inv.name}</b>{inv.invitedBy ? <small className="muted"> · from {inv.invitedBy}</small> : null}</span>
                <span style={{ display: "flex", gap: 6 }}>
                  <button className="btn" style={{ width: "auto", padding: "4px 10px" }} onClick={() => act(() => acceptAllianceInvite(inv.allianceId))}>Accept</button>
                  <button className="btn ghost" style={{ width: "auto", padding: "4px 10px" }} onClick={() => act(() => declineAllianceInvite(inv.allianceId))}>Decline</button>
                </span>
              </div>
            ))}
          </div>
        )}
        <p className="muted">Found your own alliance to band together with other players.</p>
        {!creating ? (
          <button className="btn" onClick={() => { setErr(""); setCreating(true); }}>➕ Create alliance</button>
        ) : (
          <>
            <label className="found-field"><span>Tag (2–6 chars)</span>
              <input value={tag} maxLength={6} placeholder="TAG" onChange={e => setTag(e.target.value)} /></label>
            <label className="found-field"><span>Name (3–32 chars)</span>
              <input value={name} maxLength={32} placeholder="Alliance name" onChange={e => setName(e.target.value)} /></label>
            <div style={{ display: "flex", gap: 8 }}>
              <button className="btn ghost" onClick={() => setCreating(false)}>Cancel</button>
              <button className="btn" disabled={busy || tag.trim().length < 2 || name.trim().length < 3}
                onClick={() => act(() => createAlliance(tag.trim(), name.trim())).then(() => setCreating(false))}>
                {busy ? "Creating…" : "Found alliance"}
              </button>
            </div>
          </>
        )}
      </div>
    );
  }

  // --- in an alliance: members, invite (leader), forum ---
  return (
    <div className="popup-panel ally-panel">
      <h3>[{view.tag}] {view.name}{view.isLeader && <small className="muted"> · you lead</small>}</h3>

      <div className="ally-section">
        <strong className="ally-sub">Members ({view.members?.length ?? 0})</strong>
        <div className="ally-members">
          {view.members?.map(m => (
            <div className="ally-member" key={m.id}>
              <span>{m.leader ? "👑 " : ""}{m.name}</span>
              <small className="muted">Lv {m.level}</small>
            </div>
          ))}
        </div>
      </div>

      {view.isLeader && (
        <div className="ally-section">
          <strong className="ally-sub">Invite a player</strong>
          <div style={{ display: "flex", gap: 6 }}>
            <input className="ally-input" value={invitee} placeholder="Username" onChange={e => setInvitee(e.target.value)} />
            <button className="btn" style={{ width: "auto", padding: "7px 14px" }}
              disabled={busy || !invitee.trim()} onClick={() => act(() => inviteToAlliance(invitee.trim())).then(() => setInvitee(""))}>Invite</button>
          </div>
          {view.pendingInvites && view.pendingInvites.length > 0 &&
            <small className="muted">Pending: {view.pendingInvites.map(p => p.name).join(", ")}</small>}
        </div>
      )}

      <div className="ally-section">
        <strong className="ally-sub">Forum</strong>
        <div className="ally-forum-post">
          <textarea className="msg-input" rows={2} value={post} placeholder="Post to your alliance…"
            onChange={e => setPost(e.target.value)} />
          <button className="btn" disabled={busy || !post.trim()}
            onClick={() => act(() => postAllianceForum(post.trim())).then(() => setPost(""))}>Post</button>
        </div>
        <div className="ally-forum">
          {view.forum && view.forum.length > 0
            ? view.forum.map(p => (
              <div className="ally-forum-row" key={p.id}>
                <div className="ally-forum-head"><b>{p.author}</b><small className="muted">{new Date(p.at).toLocaleString()}</small></div>
                <div>{p.body}</div>
              </div>
            ))
            : <p className="muted">No posts yet. Start the conversation.</p>}
        </div>
      </div>
    </div>
  );
}

function PlayerCrest({ player, onClick }: { player: PlayerDto; onClick?: () => void }) {
  return (
    <div className="crest clickable" title={`${player.username} — open profile`} onClick={onClick} role="button">
      <div className="crest-shield">
        <svg viewBox="0 0 60 64" width="58" height="62">
          <defs>
            <clipPath id="shieldClip"><path d="M30 2 L56 10 V32 C56 48 44 58 30 62 C16 58 4 48 4 32 V10 Z" /></clipPath>
          </defs>
          <path d="M30 2 L56 10 V32 C56 48 44 58 30 62 C16 58 4 48 4 32 V10 Z" fill="#d8ad53" stroke="#7a5a1e" strokeWidth="2.5" />
          <g clipPath="url(#shieldClip)">
            <rect x="0" y="0" width="60" height="64" fill="#e9dcc0" />
            <circle cx="30" cy="24" r="11" fill="#e8b98a" />
            <path d="M19 23 C19 14 41 14 41 23 C41 18 36 12 30 12 C24 12 19 18 19 23 Z" fill="#6e4a2a" />
            <path d="M22 26 q8 9 16 0 q1 8 -8 9 q-9 -1 -8 -9 Z" fill="#7a5230" />
            <path d="M18 40 C18 32 42 32 42 40 L42 64 L18 64 Z" fill="#5b8f3a" />
            <path d="M30 40 L34 50 L30 52 L26 50 Z" fill="#cdbf9e" opacity="0.6" />
          </g>
        </svg>
        <span className="crest-lv">Lv {player.level}</span>
      </div>
      <div className="crest-meta">
        <span className="crest-name">{player.username}</span>
        <span className="crest-pts">⚔ {fmt(player.combatPoints)} <small>cp</small></span>
      </div>
    </div>
  );
}

function ResourceBar({ active, gold }: { active: CityDetail; gold: number }) {
  const r = active.resources;
  const res = (k: string, v: number, max: number, full?: boolean) => (
    <div className={"rescard" + (full ? " full" : "")} key={k}>
      <ResIcon kind={k} />
      <span className="rescard-val">{fmt(v)}<small>/{fmt(max)}</small></span>
    </div>
  );
  return (
    <div className="resbar">
      {res("wood", r.wood, r.capacity, r.wood >= r.capacity)}
      {res("stone", r.stone, r.capacity, r.stone >= r.capacity)}
      {res("wheat", r.wheat, r.capacity, r.wheat >= r.capacity)}
      {res(resKey(r.specialResource), r.special, r.capacity, r.special >= r.capacity)}
      {Object.entries(r.otherSpecials || {}).map(([id, v]) => res(resKey(id), v, r.capacity, v >= r.capacity))}
      <div className="rescard" title={`${active.pop}/${active.maxPop} population used`}>
        <ResIcon kind="pop" />
        <span className="rescard-val">{Math.max(0, active.maxPop - active.pop)}</span>
      </div>
      <div className="rescard premium">
        <ResIcon kind="gold" />
        <span className="rescard-val">{fmt(gold)}<small>gold</small></span>
      </div>
    </div>
  );
}

function CityTab({ active, now, counts, setCounts, onBuild, onTrain, onCancel, onFinish, onFound, onCallGuard }: {
  active: CityDetail; now: number; counts: Record<string, number>;
  setCounts: (c: Record<string, number>) => void;
  onBuild: (t: string) => void; onTrain: (t: string, c: number) => void;
  onCancel: (j: number) => void; onFinish: (j: number) => void; onFound: () => void;
  onCallGuard: () => void;
}) {
  const [selectedBuilding, setSelectedBuilding] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [showLibrary, setShowLibrary] = useState(false);
  const [showSpy, setShowSpy] = useState(false);
  const [showMarket, setShowMarket] = useState(false);
  const r = active.resources;
  const afford = (cost: number[]) => r.wood >= cost[0] && r.stone >= cost[1] && r.wheat >= cost[2];
  const constructing = new Set(active.queues.BUILDING.map(j => j.label));
  const buildQueueFull = active.queues.BUILDING.length >= 5;
  const queuedSame = (t: string) => active.queues.BUILDING.filter(j => j.label === t).length;
  const freePop = active.maxPop - active.pop;
  const lack = (have: number, need: number) => have < need ? " lack" : "";

  const selected = selectedBuilding ? active.buildings.find(b => b.type === selectedBuilding) ?? null : null;
  const buildingInfo: Record<string, string> = {
    SENATE: "The heart of your city. Raise its level to unlock new construction.",
    BARRACKS: "Train troops, house soldiers and strengthen your army.",
    HARBOR: "Build naval units and send attacks across the sea.",
    LIBRARY: "Spend research points on a tree of arcane knowledge — War, Wards, Lore & Dominion.",
    WAREHOUSE: "Increase resource storage and protect your stockpile.",
    QUARRY: "Produce stone for construction and military upgrades.",
    FARM: "Grow food to support a larger population and faster growth.",
    MINE: "Grow wheat to feed your city, troops and trade.",
    EXTRACTOR: "Extract your race's special resource — needed to train elite units.",
    TIMBER: "Harvest timber to fuel construction across the city.",
    MARKET: "Trade resources for gold. A higher level carries more per convoy, runs more convoys at once, and delivers faster.",
    ALTAR: "Perform Rituals at the Altar to earn Influence — raise your level to unlock more city slots.",
    WATCHTOWER: "Spy enemy cities before attacking. A higher Watchtower is more likely to spy successfully and to catch enemy spies scouting you.",
    GARRISON: "Hold troops and reinforce your city defenses.",
  };

  // Place buildings on the painted island scene (terrain.svg, viewBox 1000x760).
  // Each entry in PLACEMENTS pins a game building type to a fixed ground point;
  // buildings without a painted plot (e.g. WALL, EXTRACTOR) fall through to the
  // structures chip-row so they stay reachable. The data layer is untouched.
  const byType = new Map(active.buildings.map(b => [b.type, b]));
  const placed = PLACEMENTS
    .map(p => ({ p, b: byType.get(p.type) }))
    .filter((e): e is { p: Placement; b: BuildingDto } => !!e.b);
  // Quick-upgrade affordability for the info panel (mirrors the drawer's logic).
  const upgradeInfo = (b: BuildingDto) => {
    const targetLv = b.level + queuedSame(b.type) + 1;
    const maxed = b.atMax || targetLv > b.max;
    const popShort = b.pop > 0 && freePop < b.pop;
    const disabled = maxed || !afford(b.cost) || popShort || buildQueueFull;
    const label = b.atMax || maxed ? "Maxed"
      : buildQueueFull ? "Queue full"
      : popShort ? "Need pop"
      : !afford(b.cost) ? "Need res"
      : b.level === 0 && queuedSame(b.type) === 0 ? "Build"
      : "Upgrade";
    return { disabled, label };
  };

  const deselect = () => { setSelectedBuilding(null); setDrawerOpen(false); };

  return (
    <div className="city-view">
      {/* painted island scene — terrain base + clickable building groups, all in
          one viewBox so they scale together with the container width */}
      <div className="city-scene">
        <svg className="city-svg" viewBox={`0 0 ${SCENE_W} ${SCENE_H}`} preserveAspectRatio="xMidYMid meet">
          <image href={TERRAIN_URL} x="0" y="0" width={SCENE_W} height={SCENE_H}
            onClick={deselect} style={{ cursor: "default" }} />
          {placed.map(({ p, b }) => {
            const sel = selectedBuilding === p.type;
            const imgX = p.x - p.w / 2;
            const imgY = p.y - p.w * (p.base ?? ICON_BASE);
            return (
              <g key={p.type}
                className={"bld-grp" + (sel ? " sel" : "")}
                style={{ transformOrigin: `${p.x}px ${p.y}px` }}
                onClick={(e) => { e.stopPropagation(); setSelectedBuilding(p.type); }}>
                {sel && <ellipse className="bld-ring" cx={p.x} cy={p.y}
                  rx={p.w * 0.34} ry={p.w * 0.34 * 0.32} />}
                <image href={p.icon} x={imgX} y={imgY} width={p.w} height={p.w} />
              </g>
            );
          })}
        </svg>

        {/* building name labels — HTML overlay, not svg <text> */}
        <div className="city-labels">
          {placed.map(({ p, b }) => (
            <span key={p.type} className="city-label"
              style={{ left: `${(p.x / SCENE_W) * 100}%`, top: `${((p.y + 13) / SCENE_H) * 100}%` }}>
              {p.name}{constructing.has(p.type) ? " 🔨" : b.level > 0 ? ` · ${b.level}` : ""}
            </span>
          ))}
        </div>
      </div>

      {/* construction queue bar (bottom-centre) */}
      <ConstructionBar jobs={active.queues.BUILDING} now={now} onCancel={onCancel} onFinish={onFinish} />

      {/* selected-building info panel (bottom-left) */}
      {selected && (() => {
        const sp = PLACEMENT_BY_TYPE[selected.type];
        const up = upgradeInfo(selected);
        return (
          <div className="city-info">
            <button className="ci-close" onClick={deselect}>✕</button>
            <div className="ci-head">
              {sp && <img className="ci-thumb" src={sp.icon} alt="" />}
              <div className="ci-titles">
                <h3>{sp?.name || titleCase(selected.type)}</h3>
                <div className="ci-sub">Level {selected.level}{selected.atMax ? " (max)" : ""} · {sp?.tag || titleCase(selected.type)}</div>
              </div>
            </div>
            <p className="ci-desc">{buildingInfo[selected.type] || ""}</p>
            <div className="ci-actions">
              <button className="ci-enter" onClick={() => setDrawerOpen(true)}>Enter</button>
              <button className="ci-upgrade" disabled={up.disabled} onClick={() => onBuild(selected.type)}>⬆ {up.label}</button>
            </div>
          </div>
        );
      })()}

      {/* primary action toolbar */}
      <div className="city-action-bar">
        <button className="cab-btn" onClick={() => { setSelectedBuilding("BARRACKS"); setDrawerOpen(true); }}><span className="cab-ico">⚔</span>Recruit</button>
        <button className="cab-btn" onClick={() => setShowMarket(true)}><span className="cab-ico">🔁</span>Trade</button>
        <button className="cab-btn" onClick={() => setShowSpy(true)}><span className="cab-ico">🕵</span>Spy</button>
      </div>

      {/* building detail drawer (opened via the info panel's "Enter") */}
      {selected && drawerOpen && (
        <div className="bld-drawer-backdrop" onClick={() => setDrawerOpen(false)}>
          <div className="bld-drawer" onClick={e => e.stopPropagation()}>
            <button className="bld-drawer-close" onClick={() => setDrawerOpen(false)}>✕</button>
            <div className="bld-drawer-head">
              <span className="bld-art big" dangerouslySetInnerHTML={{ __html: buildingSvg(selected.type, selected.level) }} />
              <div className="bld-head-text">
                <h2>{titleCase(selected.type)}</h2>
                <div className="muted">Level {selected.level}{selected.atMax ? " (max)" : ""}</div>
                <p className="muted" style={{ marginTop: 4 }}>{buildingInfo[selected.type] || ""}</p>
              </div>

              {selected.atMax
                ? <div className="bld-upgrade-panel"><div className="bld-maxed">Max level reached</div></div>
                : (() => {
                const targetLv = selected.level + queuedSame(selected.type) + 1;
                const maxed = targetLv > selected.max;
                const popNeed = selected.pop;                 // 0 for FARM — needs no population
                const popShort = popNeed > 0 && freePop < popNeed;
                const disabled = !afford(selected.cost) || popShort || buildQueueFull || maxed;
                const label = buildQueueFull ? "Queue full"
                  : maxed ? "Max level"
                  : popShort ? "Need population"
                  : selected.level === 0 && queuedSame(selected.type) === 0 ? "Build"
                  : "Upgrade";
                return (
                  <div className="bld-upgrade-panel">
                    <button className="btn bld-upgrade-btn" disabled={disabled} onClick={() => onBuild(selected.type)}>
                      ⬆ {label}{!maxed && <span className="bld-upgrade-lv"> · Lv {targetLv}</span>}
                    </button>
                    <div className="bld-cost-line">
                      <span className={"costitem" + lack(r.wood, selected.cost[0])}>🪵 {fmt(selected.cost[0])}</span>
                      <span className={"costitem" + lack(r.stone, selected.cost[1])}>🪨 {fmt(selected.cost[1])}</span>
                      <span className={"costitem" + lack(r.wheat, selected.cost[2])}>🌾 {fmt(selected.cost[2])}</span>
                      {popNeed > 0 && <span className={"costitem" + lack(freePop, popNeed)}>👥 {popNeed}</span>}
                      <span className="costitem">⏱ {clock(selected.seconds)}</span>
                    </div>
                    {selected.benefit && (
                      <div className="bld-improvements">
                        <div className="bld-impr-head">Lv {targetLv} improvements</div>
                        {selected.benefit.split(" · ").map((part, i) => (
                          <div className="bld-impr-row" key={i}>↑ {part}</div>
                        ))}
                      </div>
                    )}
                    {queuedSame(selected.type) > 0 && <small className="muted">{queuedSame(selected.type)} upgrade(s) queued</small>}
                  </div>
                );
              })()}
            </div>

            {selected.type === "FARM" && active.cityGuardEnabled && (() => {
              const readyAt = active.cityGuardReadyAt ? new Date(active.cityGuardReadyAt).getTime() : 0;
              const remaining = Math.max(0, Math.round((readyAt - now) / 1000));
              const ready = remaining <= 0;
              return (
                <div className="bld-section city-guard">
                  <h3>🧑‍🌾 City Guard</h3>
                  <p className="muted">Rally the farmers into a militia that joins this city's garrison. They're weak, but they buy time when raided.</p>
                  <button className="btn" disabled={!ready} onClick={onCallGuard}>
                    {ready ? "🧑‍🌾 Call the Guard" : `Resting — ready in ${clock(remaining)}`}
                  </button>
                </div>
              );
            })()}

            {(selected.type === "BARRACKS" || selected.type === "HARBOR") && (
              <div className="bld-section barracks-layout">
                <div className="barracks-train">
                {(() => {
                  const isHarbor = selected.type === "HARBOR";
                  if (selected.level === 0)
                    return <p className="muted">Build the {isHarbor ? "Harbor" : "Barracks"} first, then you can {isHarbor ? "build ships here" : "train troops here"}.</p>;
                  const list = active.trainable.filter(u => u.from === selected.type);
                  const card = (u: Trainable) => {
                    const specShort = u.elite && u.specialResource ? r.special < u.costSpecial : false;
                    const atkLabel = u.siege ? "💥 Siege" : (u.attackElement ? `${ELEMENT_GLYPH[u.attackElement]} ${ELEMENT_NAME[u.attackElement]}` : "");
                    return (
                    <div className={"card" + (u.elite ? " elite-unit" : "")} key={u.type}>
                      <h3><UnitTooltip type={u.type}>{titleCase(u.type)}</UnitTooltip>{u.elite ? " ⭐" : ""}</h3>
                      <div className="muted">{atkLabel} · ⚔ {u.atk}</div>
                      <div className="muted" title="Defence by element">🔥 {u.defFire} · 🌬 {u.defWind} · 🌍 {u.defEarth} · 💧 {u.defWater} · 🐢 {u.speed}{u.carry ? ` · 🎒${u.carry}` : ""}</div>
                      <div className="muted unit-move">
                        <span className={"move-badge mv-" + u.movementClass.toLowerCase()} title={MOVE_LABEL[u.movementClass]}>
                          {MOVE_BADGE[u.movementClass]} {titleCase(u.movementClass)}</span>
                        <span title="Population cost">👥 {u.pop} pop</span>
                        {u.transportCapacity > 0 && <span title="Carries land troops across water">🛳 carries {u.transportCapacity} pop</span>}
                      </div>
                      <div className="cost">🪵 {u.cost[0]} · 🪨 {u.cost[1]} · 🌾 {u.cost[2]}
                        {u.elite && u.specialResource ? <> · <span className={specShort ? "lack" : ""}>{RES_GLYPH[resKey(u.specialResource)]} {u.costSpecial}</span></> : null}
                        {" "}· ⏱ {clock(u.seconds)}/ea</div>
                      {u.unlocked ? (
                        <div style={{ display: "flex", gap: 6 }}>
                          <input type="number" min={1} value={counts[u.type] || 1} style={{ width: 56 }}
                            onChange={e => setCounts({ ...counts, [u.type]: Math.max(1, +e.target.value) })} />
                          <button className="btn" disabled={specShort} onClick={() => onTrain(u.type, counts[u.type] || 1)}>
                            {specShort ? `Need ${RES_GLYPH[resKey(u.specialResource!)]}` : "Train"}</button>
                        </div>
                      ) : <button className="btn" disabled>🔒 Research {titleCase(String(u.type))}</button>}
                    </div>
                    );
                  };
                  const grid = (items: Trainable[]) => <div className="grid">{items.map(card)}</div>;
                  // LAND races' harbor fleets group by ship role; Newt harbors hold an aquatic army.
                  if (isHarbor && list.some(u => u.shipRole)) {
                    const roles: { role: ShipRole; label: string; desc: string }[] = [
                      { role: "TRANSPORT", label: "⛴ Transport ships", desc: "Ferry ground troops across water — carry, don't fight" },
                      { role: "DEFENSE",   label: "🛡 Defense ships",   desc: "Guard the harbor against enemy fleets" },
                      { role: "ATTACK",    label: "⚔ Attack ships",    desc: "Hunt and sink enemy fleets" },
                    ];
                    const aquatic = list.filter(u => !u.shipRole);
                    return (<>
                      <h3>⛵ Build ships</h3>
                      {roles.map(rg => { const items = list.filter(u => u.shipRole === rg.role); if (!items.length) return null;
                        return (<div key={rg.role} className="ship-role-group">
                          <div className="ship-role-head">{rg.label} <small className="muted">— {rg.desc}</small></div>
                          {grid(items)}
                        </div>); })}
                      {aquatic.length > 0 && <div className="ship-role-group"><div className="ship-role-head">🌊 Aquatic army</div>{grid(aquatic)}</div>}
                    </>);
                  }
                  return (<><h3>{isHarbor ? "🌊 Train aquatic army" : "Train troops"}</h3>{grid(list)}</>);
                })()}
                </div>
                <div className="barracks-aside">
                {(selected.type === "BARRACKS" ? active.queues.BARRACKS : active.queues.HARBOR).length > 0 ? (
                  <div className="train-queue">
                    <h4>In training</h4>
                    {(() => { const tq = selected.type === "BARRACKS" ? active.queues.BARRACKS : active.queues.HARBOR;
                      return tq.map((j) => {
                      const rem = remaining(j.finishAt, now);
                      const pct = rem != null ? Math.round((1 - rem / j.totalSeconds) * 100) : 0;
                      // troop batches are independent — any batch in the queue can be rushed with gold
                      const canRush = true;
                      const rushSecs = rem ?? j.totalSeconds;
                      return (
                        <div className="tq-row" key={j.id}>
                          <span>{j.batch}× {titleCase(j.label)} {j.position > 0 && <em className="muted">#{j.position}</em>}</span>
                          <span className="tq-actions">
                            <span className="muted">{rem != null ? clock(rem) : "queued"}</span>
                            {canRush && <button className="btn ghost gold-btn" onClick={() => onFinish(j.id)}>⚡{Math.max(1, Math.ceil(rushSecs / 60))}</button>}
                            <a className="tq-cancel" onClick={() => onCancel(j.id)}>✕</a>
                          </span>
                          <div className="bar" style={{ gridColumn: "1 / -1" }}><i style={{ width: pct + "%" }} /></div>
                        </div>
                      );
                    }); })()}
                  </div>
                ) : <p className="muted tq-empty">Nothing in training yet — pick a unit and hit Train.</p>}
                </div>
              </div>
            )}

            {selected.type === "MARKET" && (
              <div className="bld-section">
                <h3>🔁 Marketplace</h3>
                <p className="muted">Buy & sell resources for gold. Sold goods travel to the buyer as trade convoys —
                  a higher-level Market carries more per convoy and ships faster.</p>
                <button className="btn" onClick={() => setShowMarket(true)}>Open the Market</button>
              </div>
            )}

            {selected.type === "LIBRARY" && selected.level > 0 && (
              <div className="bld-section">
                <h3>📚 Research tree</h3>
                <p className="muted">Spend research points across War · Wards · Lore & Dominion. The tree out-costs your
                  points, so specialize this city.</p>
                <button className="btn" onClick={() => setShowLibrary(true)}>Open the Library</button>
              </div>
            )}

            {selected.type === "ALTAR" && selected.level > 0 && (
              <AltarSection cityId={active.id} now={now} />
            )}

            {selected.type === "WATCHTOWER" && (
              <div className="bld-section">
                <h3>🕵 Espionage</h3>
                <p className="muted">Scout an enemy city's troops, resources and defences before you strike — or catch
                  their spies scouting you. Higher levels raise both your spy success and your catch chance.</p>
                <button className="btn" onClick={() => { setDrawerOpen(false); setShowSpy(true); }}>Open the Watchtower</button>
              </div>
            )}
          </div>
        </div>
      )}

      {showLibrary && <LibraryPanel cityId={active.id} onClose={() => setShowLibrary(false)} />}
      {showMarket && <TradePanel cityId={active.id} onClose={() => setShowMarket(false)} />}
      {showSpy && <SpyPanel cityId={active.id} onClose={() => setShowSpy(false)} onChanged={() => {}} />}
    </div>
  );
}

const HammerIcon = () => (
  <svg viewBox="0 0 24 24" width="26" height="26" className="cb-hammer">
    <path d="M14.5 3.5 L20.5 9.5 L18 12 L12 6 Z" fill="#9aa3ad" stroke="#5b6570" strokeWidth="1" strokeLinejoin="round" />
    <rect x="4" y="14.5" width="13" height="3.2" rx="1.4" transform="rotate(-45 10 16)" fill="#8a5a2b" stroke="#5e3a17" strokeWidth="1" />
  </svg>
);

const BUILD_QUEUE_SLOTS = 5;   // matches server-side BUILD_QUEUE_MAX
function ConstructionBar({ jobs, now, onCancel, onFinish }: { jobs: any[]; now: number; onCancel: (j: number) => void; onFinish: (j: number) => void; }) {
  const shown = jobs.slice(0, BUILD_QUEUE_SLOTS);
  return (
    <div className="city-build-bar">
      {Array.from({ length: BUILD_QUEUE_SLOTS }).map((_, i) => {
        const j = shown[i];
        if (!j) return <div className="cbslot empty" key={i}><HammerIcon /></div>;
        const rem = remaining(j.finishAt, now);
        const pct = rem != null ? Math.round((1 - rem / j.totalSeconds) * 100) : 0;
        // rushable unless an earlier slot upgrades the SAME building
        const canRush = !shown.slice(0, i).some(x => x.label === j.label);
        const rushSecs = rem ?? j.totalSeconds;
        return (
          <div className={"cbslot" + (i === 0 ? " active" : "")} key={j.id} title={`${titleCase(j.label)} → Lv ${j.toLevel}`}>
            {PLACEMENT_BY_TYPE[j.label]
              ? <span className="cbart"><img src={PLACEMENT_BY_TYPE[j.label].icon} alt={titleCase(j.label)} /></span>
              : <span className="cbart" dangerouslySetInnerHTML={{ __html: buildingSvg(j.label, j.toLevel || 1) }} />}
            <span className="cbtime">{j.position > 0 ? `#${j.position}` : rem != null ? clock(rem) : "…"}</span>
            <div className="cbbar"><i style={{ width: pct + "%" }} /></div>
            {canRush && <button className="cbrush" title={`Rush for ${Math.max(1, Math.ceil(rushSecs / 60))} gold`} onClick={() => onFinish(j.id)}>⚡{Math.max(1, Math.ceil(rushSecs / 60))}</button>}
            <a className="cbcancel" onClick={() => onCancel(j.id)}>✕</a>
          </div>
        );
      })}
    </div>
  );
}

function Queue({ title, jobs, now, onCancel, suffix }: {
  title: string; jobs: any[]; now: number; onCancel: (j: number) => void; suffix: (j: any) => string;
}) {
  if (jobs.length === 0) return null;
  return (
    <div className="panel">
      <h2>{title} queue</h2>
      {jobs.map(j => {
        const rem = remaining(j.finishAt, now);
        const pct = rem != null ? Math.round((1 - rem / j.totalSeconds) * 100) : 0;
        return (
          <div className="qrow" key={j.id} style={{ display: "block" }}>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span>{titleCase(j.label)} {suffix(j)} {j.position > 0 && <span className="pill">queued #{j.position}</span>}</span>
              <span>{rem != null ? clock(rem) : "waiting"} <a style={{ color: "var(--bad)", marginLeft: 8, cursor: "pointer" }} onClick={() => onCancel(j.id)}>✕</a></span>
            </div>
            {rem != null && <div className="bar"><i style={{ width: pct + "%" }} /></div>}
          </div>
        );
      })}
    </div>
  );
}

const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const phaseLabel = (p: string) => p === "COLONY" ? "🚢 Colony ship" : p === "OUT" ? "⚔ Attack" : "↩ Returning";
function etaLabel(arriveAt: string, now: number) {
  const s = Math.max(0, Math.round((new Date(arriveAt).getTime() - now) / 1000));
  return clock(s);
}

/** "Attack Again" from a battle report: re-raid the same target from the active city. */
function RaidAgainModal({ originCityId, originName, myUnits, heroes, target, onClose, onSend }: {
  originCityId: number; originName: string; myUnits: UnitDto[];
  heroes: Hero[];
  target: { id: number; name: string }; onClose: () => void;
  onSend: (units: Record<string, number>, heroId: number | null, intent?: string) => void;
}) {
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [heroId, setHeroId] = useState<number | null>(null);
  const [transportOk, setTransportOk] = useState(true);
  const [siege, setSiege] = useState(false);
  // a siege must be hero-led; the backend also enforces Conquest research + a Defense ship
  const siegeBlocked = siege && heroId == null;
  const send = () => {
    const units = Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
    if (Object.keys(units).length === 0 || !transportOk || siegeBlocked) return;
    onSend(units, heroId, siege ? "SIEGE" : undefined);
  };
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(460px,100%)" }}>
        <div className="modal-header">
          <h2>{siege ? "Lay siege to" : "Attack"} {target.name}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="popup-panel">
            {myUnits.length === 0 ? (
              <p className="muted">No troops in {originName}. Train some first, or switch to a city with an army.</p>
            ) : (
              <>
                <div className="raid-allrow">
                  <span className="muted">Send troops from <b>{originName}</b>:</span>
                  <span className="raid-allbtns">
                    <button type="button" className="btn ghost tiny" onClick={() => setCounts(Object.fromEntries(myUnits.map(u => [u.type, u.count])))}>Select all</button>
                    <button type="button" className="btn ghost tiny" onClick={() => setCounts({})}>Clear</button>
                  </span>
                </div>
                {myUnits.map(u => (
                  <div key={u.type} className="raid-row">
                    <span>{titleCase(u.type)} <small className="muted">({u.count} available)</small></span>
                    <input type="number" min={0} max={u.count} value={counts[u.type] || 0}
                      onChange={e => setCounts({ ...counts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
                  </div>
                ))}
                <HeroPicker heroes={heroes} value={heroId} onChange={setHeroId} />
                <label className="siege-toggle">
                  <input type="checkbox" checked={siege} onChange={e => setSiege(e.target.checked)} />
                  <span>⚑ Lay siege (Conquest)</span>
                </label>
                {siege && (
                  <p className="muted siege-note">
                    If this attack wins, an 8-hour siege begins and your <b>hero is locked in the siege</b> for the full duration.
                    Requires the <b>Conquest</b> research, your hero, and at least one <b>Defense ship</b> to anchor the blockade.
                    {siegeBlocked && <span className="siege-warn"> · Select your hero to lead the siege.</span>}
                  </p>
                )}
                <TravelPreview originCityId={originCityId} targetCityId={target.id} units={counts} heroId={heroId} onState={setTransportOk} />
                <button className="btn" disabled={!transportOk || siegeBlocked} onClick={send}>
                  {!transportOk ? "🚢 Need more transport" : siege ? "⚑ Begin the siege" : "⚔ Send attack"}</button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode; }) {
  const win = useDraggable<HTMLDivElement>();
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" ref={win} onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>{title}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}
