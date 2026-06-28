import { useEffect, useRef, useState } from "react";
import {
  getState, doBuild, doTrain, doResearch, doCancel, doRename, doFinish, getInbox, getMyMovements,
  getUnreadReportCount, doRaid, getHeroes, getFoundingStatus, getMissions,
} from "../api";
import type { GameState, CityDetail, PlayerDto, InboxMsg, PlayerMovements, UnitDto, Hero, FoundingStatus } from "../types";
import { FoundingBanner, FoundCityModal, RaceBadge } from "./FoundCity";
import MissionsPanel from "./MissionsPanel";
import InventoryModal from "./InventoryModal";
import LibraryPanel from "./LibraryPanel";
import { buildingSvg, constructionSvg, emptyPlotSvg } from "../buildings";
import WorldView from "./WorldView";
import Rankings from "./Rankings";
import MovementsOverview from "./MovementsOverview";
import BattleReports from "./BattleReports";
import HeroPanel from "./HeroPanel";
import { CityMovementsPanel, TravelPreview, UnitTooltip, ATTACK_GLYPH, HeroPicker } from "../movements";

const fmt = (n: number) => n >= 10000 ? (n / 1000).toFixed(n >= 100000 ? 0 : 1) + "k" : Math.floor(n).toString();
const RES_GLYPH: Record<string, string> = { wood: "🪵", stone: "🪨", silver: "🪙", favor: "✨" };

const ResIcon = ({ kind }: { kind: string }) => {
  const i: Record<string, React.ReactNode> = {
    wood: <g><rect x="3" y="13" width="18" height="6" rx="3" fill="#8a5a2b" stroke="#5e3a17" strokeWidth="1.2" /><ellipse cx="6" cy="16" rx="2" ry="3" fill="#c98f4e" stroke="#5e3a17" strokeWidth="1.2" /><rect x="4" y="6" width="16" height="6" rx="3" fill="#9c6a36" stroke="#5e3a17" strokeWidth="1.2" /><ellipse cx="6.5" cy="9" rx="2" ry="3" fill="#d6a05c" stroke="#5e3a17" strokeWidth="1.2" /></g>,
    stone: <g><path d="M5 18 L3 11 L8 6 L15 6 L21 12 L19 18 Z" fill="#9aa3ad" stroke="#5b6570" strokeWidth="1.3" strokeLinejoin="round" /><path d="M8 6 L11 12 L19 18 M3 11 L11 12 L5 18" fill="none" stroke="#6b7681" strokeWidth="1" /></g>,
    silver: <g><circle cx="12" cy="12" r="8.5" fill="#d9dde3" stroke="#9098a3" strokeWidth="1.4" /><circle cx="12" cy="12" r="5.5" fill="none" stroke="#aab0bb" strokeWidth="1.2" /><text x="12" y="16" fontSize="8" textAnchor="middle" fill="#8a909b" fontWeight="700">$</text></g>,
    favor: <path d="M12 2 L14.6 8.6 L21.5 9.2 L16.2 13.8 L17.9 20.6 L12 16.9 L6.1 20.6 L7.8 13.8 L2.5 9.2 L9.4 8.6 Z" fill="#ffd35a" stroke="#d6a020" strokeWidth="1.2" strokeLinejoin="round" />,
    pop: <g><circle cx="9" cy="8" r="3.4" fill="#e6c98f" stroke="#9c7a3e" strokeWidth="1.2" /><path d="M3 20 c0-4 3-6.5 6-6.5 s6 2.5 6 6.5" fill="#cf9d52" stroke="#9c7a3e" strokeWidth="1.2" /><circle cx="16" cy="9" r="2.8" fill="#d9b878" stroke="#9c7a3e" strokeWidth="1.2" /><path d="M13.5 20 c0-3.3 2-5.5 4.5-5.5 s4 2 4 5.5" fill="#bd8c46" stroke="#9c7a3e" strokeWidth="1.2" /></g>,
    gold: <g><circle cx="12" cy="12" r="8.5" fill="#f2c94c" stroke="#b8860b" strokeWidth="1.6" /><circle cx="12" cy="12" r="5.5" fill="none" stroke="#d9a520" strokeWidth="1.2" /><text x="12" y="16" fontSize="9" textAnchor="middle" fill="#9a6b08" fontWeight="700">★</text></g>,
  };
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
  const [modal, setModal] = useState<"rankings" | "profile" | "alliance" | "messages" | null>(null);
  const [err, setErr] = useState("");
  const [now, setNow] = useState(Date.now());
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [editing, setEditing] = useState(false);
  const [nameDraft, setNameDraft] = useState("");
  const [moves, setMoves] = useState<PlayerMovements | null>(null);
  const [showMoves, setShowMoves] = useState(false);
  const [showReports, setShowReports] = useState(false);
  const [unreadReports, setUnreadReports] = useState(0);
  const [raidAgain, setRaidAgain] = useState<{ id: number; name: string } | null>(null);
  const [showHero, setShowHero] = useState(false);
  const [heroes, setHeroes] = useState<Hero[]>([]);
  const [founding, setFounding] = useState<FoundingStatus["founding"]>(null);
  const [chooseRace, setChooseRace] = useState(false);
  const [showMissions, setShowMissions] = useState(false);
  const [showInv, setShowInv] = useState(false);
  const [claimable, setClaimable] = useState(0);
  const [activeMission, setActiveMission] = useState<string | null>(null);
  const [nudgeDismissed, setNudgeDismissed] = useState(false);
  const polling = useRef<number>();

  const refreshUnreadReports = () => getUnreadReportCount().then(r => setUnreadReports(r.count)).catch(() => {});
  const refreshHeroes = () => getHeroes().then(setHeroes).catch(() => {});
  const refreshFounding = () => getFoundingStatus().then(s => setFounding(s.founding)).catch(() => {});
  const refreshMissions = () => getMissions().then(m => {
    setClaimable(m.missions.filter(x => x.status === "COMPLETED").length);
    const first = m.missions.find(x => x.status === "ACTIVE");
    setActiveMission(first ? `${first.title} — ${first.description}` : null);
  }).catch(() => {});

  useEffect(() => { if (!err) return; const t = setTimeout(() => setErr(""), 3500); return () => clearTimeout(t); }, [err]);

  async function refresh(cityId = activeCityId) {
    try {
      const s = await getState(cityId);
      setState(s);
      if (cityId === undefined) setActiveCityId(s.active.id);
    } catch (e: any) {
      const m = String(e.message);
      if (m.includes("401") || m.includes("403")) onLogout(); else setErr(m);
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
          <PlayerCrest player={player} />
          <button className="logout" onClick={onLogout}>log out</button>
        </div>
      </div>

      {err && <div className="toast" onClick={() => setErr("")}>{err}</div>}

      {founding && <FoundingBanner founding={founding} now={now}
        onChoose={() => { setTab("world"); setChooseRace(true); }} />}

      {!nudgeDismissed && activeMission && (
        <div className="mission-nudge">
          <span>📜 Next: <b>{activeMission}</b></span>
          <button className="btn ghost" onClick={() => setShowMissions(true)}>View missions</button>
          <button className="mission-nudge-x" onClick={() => setNudgeDismissed(true)}>✕</button>
        </div>
      )}

      <div className="view-toggle">
        <button className={"view-btn" + (tab === "city" ? " active" : "")} onClick={() => setTab("city")}>City View</button>
        <button className={"view-btn" + (tab === "world" ? " active" : "")} onClick={() => setTab("world")}>World View</button>
      </div>

      <div className="fullscreen-view">
        {tab === "city" && <CityTab key={active.id} active={active} now={now} counts={counts} setCounts={setCounts}
          onBuild={(t) => action(() => doBuild(active.id, t))()}
          onTrain={(t, c) => action(() => doTrain(active.id, t, c))()}
          onResearch={(t) => action(() => doResearch(active.id, t))()}
          onCancel={(j) => action(() => doCancel(active.id, j))()}
          onFinish={(j) => action(() => doFinish(active.id, j))()} />}

        {tab === "world" && <WorldView activeCityId={active.id} myUnits={active.units} heroes={heroes} myPlayerId={player.id} onChanged={() => { refresh(); refreshHeroes(); }} setErr={setErr} />}

        {tab === "city" && <TroopsPanel units={active.units} />}
        {tab === "city" && <CityMovementsPanel cityId={active.id} now={now}
          onAttackAgain={(id, name) => setRaidAgain({ id, name })} />}

        <div className="panel-actions">
          <button className={"panel-action-btn" + (hostileInbound ? " alert" : "")} onClick={() => setShowMoves(true)}>
            🪖 Movements
            {moveCount > 0 && <span className={"nav-badge" + (hostileInbound ? " hostile" : "")}>{moveCount}</span>}
          </button>
          <button className="panel-action-btn" onClick={() => setShowReports(true)}>
            📜 Reports
            {unreadReports > 0 && <span className="nav-badge hostile">{unreadReports}</span>}
          </button>
          <button className="panel-action-btn" onClick={() => setShowHero(true)}>
            🏛 Heroes
            {heroes.some(h => h.unlocked && h.unspentAttributePoints > 0) &&
              <span className="nav-badge">{heroes.reduce((a, h) => a + (h.unlocked ? h.unspentAttributePoints : 0), 0)}</span>}
          </button>
          <button className="panel-action-btn" onClick={() => setShowMissions(true)}>
            📜 Missions
            {claimable > 0 && <span className="nav-badge">{claimable}</span>}
          </button>
          <button className="panel-action-btn" onClick={() => setShowInv(true)}>🎒 Inventory</button>
          <button className="panel-action-btn" onClick={() => setModal("rankings")}>Rankings</button>
          <button className="panel-action-btn" onClick={() => setModal("profile")}>Profile</button>
          <button className="panel-action-btn" onClick={() => setModal("alliance")}>Alliance</button>
          <button className="panel-action-btn" onClick={() => setModal("messages")}>Messages</button>
        </div>
      </div>

      {showMoves && <MovementsOverview data={moves} now={now} onClose={() => setShowMoves(false)} onGoCity={goToCity} />}

      {showReports && <BattleReports cities={cities} unreadCount={unreadReports}
        onClose={() => setShowReports(false)} onUnreadChange={refreshUnreadReports}
        onAttackAgain={(id, name) => { setShowReports(false); setRaidAgain({ id, name }); }} />}

      {showHero && <HeroPanel cities={cities} onClose={() => setShowHero(false)} onChanged={refreshHeroes} />}

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
        onSend={async (units, heroId) => {
          setErr("");
          try { await doRaid(active.id, raidAgain.id, units, heroId); setRaidAgain(null); await refresh(); refreshHeroes(); }
          catch (e: any) { setErr(e.message); }
        }} />}

      {modal === "rankings" && <Modal title="Rankings" onClose={() => setModal(null)}><Rankings /></Modal>}
      {modal === "profile" && <Modal title="Profile" onClose={() => setModal(null)}>
        <div className="popup-panel">
          <h3>{player.username}</h3>
          <div className="popup-grid">
            <div><strong>Level</strong><span>{player.level}</span></div>
            <div><strong>Points</strong><span>{fmt(player.totalPoints)}</span></div>
            <div><strong>Cities</strong><span>{player.ownedCities}</span></div>
            <div><strong>Alliance</strong><span>{player.alliance || "None"}</span></div>
          </div>
        </div>
      </Modal>}
      {modal === "alliance" && <Modal title="Alliance" onClose={() => setModal(null)}>
        <div className="popup-panel">
          <h3>{player.alliance || "No alliance"}</h3>
          <p className="muted">Your alliance status and diplomacy information will appear here.</p>
          <div className="popup-grid">
            <div><strong>Members</strong><span>—</span></div>
            <div><strong>Power</strong><span>—</span></div>
            <div><strong>Rank</strong><span>—</span></div>
          </div>
        </div>
      </Modal>}
      {modal === "messages" && <Modal title="Messages" onClose={() => setModal(null)}><Inbox /></Modal>}
    </div>
  );
}

function TroopsPanel({ units }: { units: { type: string; count: number }[] }) {
  return (
    <div className="troops-panel">
      <h3>Troops</h3>
      {units.length === 0
        ? <p className="muted tp-empty">No troops yet. Train them in the Barracks or Harbor.</p>
        : units.map(u => (
          <div className="tp-row" key={u.type}>
            <span className="tp-name"><UnitTooltip type={u.type}>⚔ {titleCase(u.type)}</UnitTooltip></span>
            <span className="tp-count">{u.count}</span>
          </div>
        ))}
    </div>
  );
}

function Inbox() {
  const [msgs, setMsgs] = useState<InboxMsg[] | null>(null);
  useEffect(() => { getInbox().then(setMsgs).catch(() => setMsgs([])); }, []);
  if (!msgs) return <p className="muted">Loading…</p>;
  if (msgs.length === 0) return <div className="popup-panel"><h3>Inbox</h3><p className="muted">No messages yet. Other players can message you from the world map.</p></div>;
  return (
    <div className="popup-panel">
      <h3>Inbox</h3>
      {msgs.map(m => (
        <div className="msg-row" key={m.id}>
          <div className="msg-head"><strong>{m.from}</strong><small className="muted">{new Date(m.sentAt).toLocaleString()}</small></div>
          <div>{m.body}</div>
        </div>
      ))}
    </div>
  );
}

function PlayerCrest({ player }: { player: PlayerDto }) {
  return (
    <div className="crest" title={player.username}>
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
      {res("silver", r.silver, r.capacity, r.silver >= r.capacity)}
      {(r.favor > 0 || r.favorProd > 0) && res("favor", r.favor, r.capacity, r.favor >= r.capacity)}
      <div className="rescard">
        <ResIcon kind="pop" />
        <span className="rescard-val">{active.pop}<small>/{active.maxPop}</small></span>
      </div>
      <div className="rescard premium">
        <ResIcon kind="gold" />
        <span className="rescard-val">{fmt(gold)}<small>gold</small></span>
      </div>
    </div>
  );
}

function CityTab({ active, now, counts, setCounts, onBuild, onTrain, onResearch, onCancel, onFinish }: {
  active: CityDetail; now: number; counts: Record<string, number>;
  setCounts: (c: Record<string, number>) => void;
  onBuild: (t: string) => void; onTrain: (t: string, c: number) => void;
  onResearch: (t: string) => void; onCancel: (j: number) => void; onFinish: (j: number) => void;
}) {
  const [selectedBuilding, setSelectedBuilding] = useState<string | null>(null);
  const [showLibrary, setShowLibrary] = useState(false);
  const r = active.resources;
  const afford = (cost: number[]) => r.wood >= cost[0] && r.stone >= cost[1] && r.silver >= cost[2];
  const constructing = new Set(active.queues.BUILDING.map(j => j.label));
  const buildQueueFull = active.queues.BUILDING.length >= 5;
  const queuedSame = (t: string) => active.queues.BUILDING.filter(j => j.label === t).length;
  const freePop = active.maxPop - active.pop;
  const lack = (have: number, need: number) => have < need ? " lack" : "";

  const selected = selectedBuilding ? active.buildings.find(b => b.type === selectedBuilding) ?? null : null;
  const buildingInfo: Record<string, string> = {
    SENATE: "The heart of your city. Raise its level to unlock new construction.",
    BARRACKS: "Train troops, house soldiers and strengthen your army.",
    HARBOR: "Build naval units and send raids across the sea.",
    ACADEMY: "The Library. Spend research points on a tree of arcane knowledge — War, Wards, Lore & Dominion.",
    WAREHOUSE: "Increase resource storage and protect your stockpile.",
    TEMPLE: "Gain favor from the gods and unlock divine blessings.",
    QUARRY: "Produce stone for construction and military upgrades.",
    FARM: "Grow food to support a larger population and faster growth.",
    MINE: "Extract silver for trade, army upkeep and city development.",
    TIMBER: "Harvest timber to fuel construction across the city.",
    AGORA: "Marketplace and civic gathering place of the polis.",
    WALL: "Fortify your city against raids and invasions.",
    GARRISON: "Hold troops and reinforce your city defenses.",
  };

  // Place buildings on a town map: senate at the centre, the rest spread across
  // concentric rings (mimics the ringed-road layout).
  const center = active.buildings.find(b => b.type === "SENATE");
  const others = active.buildings.filter(b => b.type !== "SENATE");
  // distribute roughly a third per ring so all three rings are populated
  const per = Math.ceil(others.length / 3);
  // Keep the cluster inside a central band so no tile lands under the floating
  // HUD panels (troops/movements on the right ~x>73%, panel-actions on the left
  // ~x<13%) which sit above tiles in z-order and would swallow their clicks.
  // Bias slightly left (right side holds two panels) and shrink horizontal radius.
  const cx = 43, cy = 50;
  const rings = [
    { rx: 14, ry: 14, cap: per, start: -90 },
    { rx: 22, ry: 26, cap: per, start: -64 },
    { rx: 28, ry: 36, cap: others.length, start: -78 },
  ];
  const placed: { b: typeof others[number]; x: number; y: number }[] = [];
  let idx = 0;
  for (const ring of rings) {
    const slice = others.slice(idx, idx + ring.cap);
    slice.forEach((b, i) => {
      const a = (ring.start + (360 / Math.max(slice.length, 1)) * i) * Math.PI / 180;
      placed.push({ b, x: cx + ring.rx * Math.cos(a), y: cy + ring.ry * Math.sin(a) });
    });
    idx += ring.cap;
  }

  const Badge = ({ lv, building }: { lv: number; building: boolean }) =>
    <span className={"bld-badge" + (building ? " constructing" : "")}>{building ? "⛏" : lv}</span>;

  return (
    <div className="city-view">
      <svg className="city-map-svg" viewBox="0 0 100 100" preserveAspectRatio="xMidYMid slice">
        <defs>
          <radialGradient id="grass" cx="50%" cy="46%" r="65%">
            <stop offset="0%" stopColor="#8ec64a" /><stop offset="100%" stopColor="#6fae36" />
          </radialGradient>
        </defs>
        <rect x="0" y="0" width="100" height="100" fill="url(#grass)" />
        {/* river */}
        <path d="M-5 30 Q30 42 50 46 Q72 50 105 78" fill="none" stroke="#4aa3df" strokeWidth="9" strokeLinecap="round" opacity="0.92" />
        <path d="M-5 30 Q30 42 50 46 Q72 50 105 78" fill="none" stroke="#6cc0ee" strokeWidth="4.5" strokeLinecap="round" opacity="0.7" />
        {/* ring roads */}
        {rings.map((rg, i) =>
          <ellipse key={i} cx="50" cy="50" rx={rg.rx} ry={rg.ry} fill="none" stroke="#cdb98f" strokeWidth={i === 2 ? 5 : 3.2} opacity="0.85" />
        )}
        <ellipse cx="50" cy="50" rx="8" ry="6" fill="#d8c79c" opacity="0.6" />
      </svg>

      {/* central senate */}
      {center && (
        <button className="bld-tile bld-center" style={{ left: cx + "%", top: cy + "%" }}
          onClick={() => setSelectedBuilding("SENATE")} title="Senate">
          <span className="bld-art" dangerouslySetInnerHTML={{ __html: constructing.has("SENATE") ? constructionSvg() : buildingSvg("SENATE", center.level) }} />
          <Badge lv={center.level} building={constructing.has("SENATE")} />
        </button>
      )}

      {/* ringed buildings */}
      {placed.map(({ b, x, y }) => {
        const buildingThis = constructing.has(b.type);
        const built = b.level > 0;
        return (
          <button className={"bld-tile" + (selectedBuilding === b.type ? " sel" : "") + (built || buildingThis ? "" : " plot")} key={b.type}
            style={{ left: x + "%", top: y + "%" }}
            onClick={() => setSelectedBuilding(b.type)}
            title={built || buildingThis ? titleCase(b.type) : `Build ${titleCase(b.type)}`}>
            <span className="bld-art" dangerouslySetInnerHTML={{ __html: buildingThis ? constructionSvg() : built ? buildingSvg(b.type, b.level) : emptyPlotSvg() }} />
            {(built || buildingThis) && <Badge lv={b.level} building={buildingThis} />}
          </button>
        );
      })}

      {/* construction queue bar (bottom-centre) */}
      <ConstructionBar jobs={active.queues.BUILDING} now={now} onCancel={onCancel} onFinish={onFinish} />

      {/* building detail drawer */}
      {selected && (
        <div className="bld-drawer-backdrop" onClick={() => setSelectedBuilding(null)}>
          <div className="bld-drawer" onClick={e => e.stopPropagation()}>
            <button className="bld-drawer-close" onClick={() => setSelectedBuilding(null)}>✕</button>
            <div className="bld-drawer-head">
              <span className="bld-art big" dangerouslySetInnerHTML={{ __html: buildingSvg(selected.type, selected.level) }} />
              <div>
                <h2>{selected.type === "ACADEMY" ? "Library" : titleCase(selected.type)}</h2>
                <div className="muted">Level {selected.level}{selected.atMax ? " (max)" : ""}</div>
                <p className="muted" style={{ marginTop: 4 }}>{buildingInfo[selected.type] || ""}</p>
              </div>
            </div>

            {!selected.atMax && (() => {
              const targetLv = selected.level + queuedSame(selected.type) + 1;
              const maxed = targetLv > selected.max;
              const popNeed = selected.pop;                 // 0 for FARM — needs no population
              const popShort = popNeed > 0 && freePop < popNeed;
              return (
                <div className="bld-upgrade">
                  <div className="cost">
                    <span className={"costitem" + lack(r.wood, selected.cost[0])}>🪵 {fmt(selected.cost[0])}</span>
                    <span className={"costitem" + lack(r.stone, selected.cost[1])}>🪨 {fmt(selected.cost[1])}</span>
                    <span className={"costitem" + lack(r.silver, selected.cost[2])}>🪙 {fmt(selected.cost[2])}</span>
                    {popNeed > 0 && <span className={"costitem" + lack(freePop, popNeed)}>👥 {popNeed} pop</span>}
                    <span className="costitem">⏱ {clock(selected.seconds)}</span>
                  </div>
                  {selected.type === "FARM" && <small className="muted">Raises your city population limit. Needs no population.</small>}
                  <button className="btn" disabled={!afford(selected.cost) || popShort || buildQueueFull || maxed}
                    onClick={() => onBuild(selected.type)}>
                    {buildQueueFull ? "Queue full (max 5)"
                      : maxed ? "Max level reached"
                      : popShort ? "Not enough population"
                      : selected.level === 0 && queuedSame(selected.type) === 0 ? "Build"
                      : `Upgrade to Lv ${targetLv}`}
                  </button>
                  {queuedSame(selected.type) > 0 && <small className="muted">{queuedSame(selected.type)} upgrade(s) queued</small>}
                </div>
              );
            })()}

            {(selected.type === "BARRACKS" || selected.type === "HARBOR") && selected.level > 0 && (
              <div className="bld-section">
                <h3>Train troops</h3>
                <div className="grid">
                  {active.trainable.filter(u => u.from === selected.type).map(u => (
                    <div className="card" key={u.type}>
                      <h3><UnitTooltip type={u.type}>{titleCase(u.type)}</UnitTooltip></h3>
                      <div className="muted">{ATTACK_GLYPH[u.attackType]} {u.attackType.charAt(0) + u.attackType.slice(1).toLowerCase()} · ⚔ {u.atk}</div>
                      <div className="muted">🔨 {u.defBlunt} · 🗡 {u.defSharp} · 🏹 {u.defDistance} · 🐢 {u.speed}{u.carry ? ` · 🎒${u.carry}` : ""}</div>
                      <div className="cost">🪵 {u.cost[0]} · 🪨 {u.cost[1]} · 🪙 {u.cost[2]} · ⏱ {clock(u.seconds)}/ea</div>
                      {u.unlocked ? (
                        <div style={{ display: "flex", gap: 6 }}>
                          <input type="number" min={1} value={counts[u.type] || 1} style={{ width: 56 }}
                            onChange={e => setCounts({ ...counts, [u.type]: Math.max(1, +e.target.value) })} />
                          <button className="btn" onClick={() => onTrain(u.type, counts[u.type] || 1)}>Train</button>
                        </div>
                      ) : <button className="btn" disabled>🔒 Research {titleCase(String(u.type))}</button>}
                    </div>
                  ))}
                </div>
                {(selected.type === "BARRACKS" ? active.queues.BARRACKS : active.queues.HARBOR).length > 0 && (
                  <div className="train-queue">
                    <h4>In training</h4>
                    {(selected.type === "BARRACKS" ? active.queues.BARRACKS : active.queues.HARBOR).map((j, i) => {
                      const rem = remaining(j.finishAt, now);
                      const pct = rem != null ? Math.round((1 - rem / j.totalSeconds) * 100) : 0;
                      return (
                        <div className="tq-row" key={j.id}>
                          <span>{j.batch}× {titleCase(j.label)} {j.position > 0 && <em className="muted">#{j.position}</em>}</span>
                          <span className="tq-actions">
                            <span className="muted">{rem != null ? clock(rem) : "queued"}</span>
                            {i === 0 && rem != null && <button className="btn ghost gold-btn" onClick={() => onFinish(j.id)}>⚡{Math.max(1, Math.ceil(rem / 60))}</button>}
                            <a className="tq-cancel" onClick={() => onCancel(j.id)}>✕</a>
                          </span>
                          <div className="bar" style={{ gridColumn: "1 / -1" }}><i style={{ width: pct + "%" }} /></div>
                        </div>
                      );
                    })}
                  </div>
                )}
                {active.units.length > 0 && (
                  <p className="muted" style={{ marginTop: 10 }}>Garrison: {active.units.map(u => `${u.count}× ${titleCase(u.type)}`).join(" · ")}</p>
                )}
              </div>
            )}

            {selected.type === "ACADEMY" && selected.level > 0 && (
              <div className="bld-section">
                <h3>📚 Research tree</h3>
                <p className="muted">Spend research points across War · Wards · Lore & Dominion. The tree out-costs your
                  points, so specialize this city.</p>
                <button className="btn" onClick={() => setShowLibrary(true)}>Open the Library</button>
                {/* legacy unit-unlock research (kept for unit gating) */}
                <div className="grid" style={{ marginTop: 10 }}>
                  {active.research.map(rs => (
                    <div className="card" key={rs.type}>
                      <h3>{titleCase(rs.type)}</h3>
                      <div className="muted">Library Lv {rs.req}+</div>
                      <div className="cost">🪵 {rs.cost[0]} · 🪨 {rs.cost[1]} · 🪙 {rs.cost[2]}</div>
                      <button className="btn" disabled={rs.done || !afford(rs.cost)} onClick={() => onResearch(rs.type)}>
                        {rs.done ? "✓ Researched" : "Research"}
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {showLibrary && <LibraryPanel cityId={active.id} onClose={() => setShowLibrary(false)} />}
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
        return (
          <div className={"cbslot" + (i === 0 ? " active" : "")} key={j.id} title={`${titleCase(j.label)} → Lv ${j.toLevel}`}>
            <span className="cbart" dangerouslySetInnerHTML={{ __html: buildingSvg(j.label, j.toLevel || 1) }} />
            <span className="cbtime">{j.position > 0 ? `#${j.position}` : rem != null ? clock(rem) : "…"}</span>
            <div className="cbbar"><i style={{ width: pct + "%" }} /></div>
            {i === 0 && rem != null && <button className="cbrush" title={`Rush for ${Math.max(1, Math.ceil(rem / 60))} gold`} onClick={() => onFinish(j.id)}>⚡{Math.max(1, Math.ceil(rem / 60))}</button>}
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
const phaseLabel = (p: string) => p === "COLONY" ? "🚢 Colony ship" : p === "OUT" ? "⚔ Raid" : "↩ Returning";
function etaLabel(arriveAt: string, now: number) {
  const s = Math.max(0, Math.round((new Date(arriveAt).getTime() - now) / 1000));
  return clock(s);
}

/** "Attack Again" from a battle report: re-raid the same target from the active city. */
function RaidAgainModal({ originCityId, originName, myUnits, heroes, target, onClose, onSend }: {
  originCityId: number; originName: string; myUnits: UnitDto[];
  heroes: Hero[];
  target: { id: number; name: string }; onClose: () => void;
  onSend: (units: Record<string, number>, heroId: number | null) => void;
}) {
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [heroId, setHeroId] = useState<number | null>(null);
  const send = () => {
    const units = Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
    if (Object.keys(units).length === 0) return;
    onSend(units, heroId);
  };
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(460px,100%)" }}>
        <div className="modal-header">
          <h2>Attack {target.name}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="popup-panel">
            {myUnits.length === 0 ? (
              <p className="muted">No troops in {originName}. Train some first, or switch to a city with an army.</p>
            ) : (
              <>
                <p className="muted">Send troops from <b>{originName}</b>:</p>
                {myUnits.map(u => (
                  <div key={u.type} className="raid-row">
                    <span>{titleCase(u.type)} <small className="muted">({u.count} available)</small></span>
                    <input type="number" min={0} max={u.count} value={counts[u.type] || 0}
                      onChange={e => setCounts({ ...counts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
                  </div>
                ))}
                <HeroPicker heroes={heroes} value={heroId} onChange={setHeroId} />
                <TravelPreview originCityId={originCityId} targetCityId={target.id} units={counts} />
                <button className="btn" onClick={send}>⚔ Send raid</button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode; }) {
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>{title}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}
