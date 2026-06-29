import { useEffect, useRef, useState } from "react";
import {
  getState, doBuild, doTrain, doCancel, doRename, doFinish, getInbox, getMyMovements,
  getUnreadReportCount, doAttack, getHeroes, getFoundingStatus, getMissions, getTradeDeliveries, assignHero,
  createAlliance, getServerTime, getMyAlliance, inviteToAlliance, acceptAllianceInvite, declineAllianceInvite, postAllianceForum,
  deassignHero,
} from "../api";
import type { GameState, CityDetail, PlayerDto, InboxMsg, PlayerMovements, UnitDto, Hero, FoundingStatus, Trainable, AllianceView } from "../types";
import { FoundingBanner, FoundCityModal, RaceBadge, RACES } from "./FoundCity";
import MissionsPanel from "./MissionsPanel";
import InventoryModal from "./InventoryModal";
import LibraryPanel from "./LibraryPanel";
import TradePanel from "./TradePanel";
import { buildingSvg, constructionSvg, emptyPlotSvg } from "../buildings";
import WorldView from "./WorldView";
import Rankings from "./Rankings";
import MovementsOverview from "./MovementsOverview";
import BattleReports from "./BattleReports";
import HeroPanel from "./HeroPanel";
import { CityMovementsPanel, TravelPreview, UnitTooltip, HeroPicker, HERO_GLYPH, UNIT_GLYPH } from "../movements";

const fmt = (n: number) => n >= 10000 ? (n / 1000).toFixed(n >= 100000 ? 0 : 1) + "k" : Math.floor(n).toString();
const RES_GLYPH: Record<string, string> = {
  wood: "🪵", stone: "🪨", wheat: "🌾",
  coal: "⬛", crystals: "💎", iron: "⛓", pearls: "🫧",
};
/** Lowercased resource key for a ResourceType id (WOOD -> wood, COAL -> coal). */
const resKey = (id: string) => id.toLowerCase();
const ELEMENT_GLYPH: Record<string, string> = { FIRE: "🔥", WIND: "🌬", EARTH: "🌍", WATER: "💧" };
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
  const [modal, setModal] = useState<"rankings" | "profile" | "alliance" | "messages" | null>(null);
  const [err, setErr] = useState("");
  const [note, setNote] = useState("");
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
    try {
      const s = await getState(cityId);
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
          <PlayerCrest player={player} />
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

      <div className={"game-shell" + (tab === "world" ? " no-info" : "")}>
        <SideNav
          unreadReports={unreadReports} claimable={claimable}
          heroPts={heroes.reduce((a, h) => a + (h.unlocked ? h.unspentAttributePoints : 0), 0)}
          serverLine={`${player.ownedCities} ${player.ownedCities === 1 ? "city" : "cities"} · ${fmt(player.totalPoints)} pts`}
          onReports={() => setShowReports(true)} onMissions={() => setShowMissions(true)}
          onHeroes={() => setShowHero(true)} onInventory={() => setShowInv(true)}
          onModal={setModal} />

        <div className="stage">
          <div className="stage-bar">
            <div className="map-tabs">
              <button className="map-tab active" onClick={() => setTab(tab === "city" ? "world" : "city")}>
                {tab === "city" ? "🌐 World View" : "🏠 Island"}
              </button>
            </div>
          </div>
          <div className="stage-body">
            {tab === "city" && <CityTab key={active.id} active={active} now={now} counts={counts} setCounts={setCounts}
              onBuild={(t) => action(() => doBuild(active.id, t))()}
              onTrain={(t, c) => action(() => doTrain(active.id, t, c))()}
              onCancel={(j) => action(() => doCancel(active.id, j))()}
              onFinish={(j) => action(() => doFinish(active.id, j))()}
              onFound={() => setTab("world")} />}

            {tab === "world" && <WorldView activeCityId={active.id} myUnits={active.units} heroes={heroes} myPlayerId={player.id} onChanged={() => { refresh(); refreshHeroes(); }} setErr={setErr} />}
          </div>
        </div>

        {tab === "city" && (
          <aside className="side-info">
            <TroopsPanel units={active.units} trainable={active.trainable} />
            <CityMovementsPanel cityId={active.id} now={now}
              onExpand={() => setShowMoves(true)}
              onAttackAgain={(id, name) => setRaidAgain({ id, name })} />
            <HeroSummary heroes={heroes} cityId={active.id} now={now} onOpen={() => setShowHero(true)}
              onChanged={refreshHeroes} setErr={setErr} />
          </aside>
        )}
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
          try { await doAttack(active.id, raidAgain.id, units, heroId); setRaidAgain(null); await refresh(); refreshHeroes(); }
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
        <AlliancePanel player={player} onChanged={() => refresh()} setErr={setErr} />
      </Modal>}
      {modal === "messages" && <Modal title="Messages" onClose={() => setModal(null)}><Inbox /></Modal>}
    </div>
  );
}

const ELEMENT_NAME: Record<string, string> = { FIRE: "Fire", WIND: "Wind", EARTH: "Earth", WATER: "Water" };

function TroopsPanel({ units, trainable }: { units: { type: string; count: number }[]; trainable: Trainable[] }) {
  const total = units.reduce((a, u) => a + u.count, 0);
  const role = (type: string) => {
    const t = trainable.find(x => x.type === type);
    if (!t) return "";
    return t.siege ? "Siege" : (t.attackElement ? ELEMENT_NAME[t.attackElement] : "");
  };
  return (
    <div className="side-card troops-panel">
      <div className="sc-head"><span className="sc-title">⚔ Troops</span><span className="sc-sub">{total} in city</span></div>
      {units.length === 0
        ? <p className="muted tp-empty">No troops yet. Train them in the Barracks or Harbor.</p>
        : units.map(u => (
          <div className="tp-row" key={u.type}>
            <span className="tp-ico">{UNIT_GLYPH[u.type] ?? "⚔"}</span>
            <span className="tp-main">
              <UnitTooltip type={u.type}><b className="tp-name">{titleCase(u.type)}</b></UnitTooltip>
              <small className="tp-role">{role(u.type)}</small>
            </span>
            <span className="tp-count">{u.count}</span>
          </div>
        ))}
    </div>
  );
}

function SideNav({ unreadReports, claimable, heroPts, serverLine, onReports, onMissions, onHeroes, onInventory, onModal }: {
  unreadReports: number; claimable: number; heroPts: number; serverLine: string;
  onReports: () => void; onMissions: () => void; onHeroes: () => void; onInventory: () => void;
  onModal: (m: "rankings" | "profile" | "alliance" | "messages") => void;
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
      <Item icon="🎒" label="Inventory" onClick={onInventory} />
      <div className="nav-group">Social</div>
      <Item icon="🤝" label="Alliance" onClick={() => onModal("alliance")} />
      <Item icon="✉" label="Messages" onClick={() => onModal("messages")} />
      <Item icon="🏆" label="Rankings" onClick={() => onModal("rankings")} />
      <Item icon="👤" label="Profile" onClick={() => onModal("profile")} />
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
  const unlocked = heroes.filter(h => h.unlocked);
  // prefer a hero already here, else an unassigned hero ready to assign, else any idle, else first
  const hero = unlocked.find(h => h.stationedCityId === cityId)
    ?? unlocked.find(h => h.stationedCityId == null && h.state === "IDLE")
    ?? unlocked.find(h => h.state === "IDLE") ?? unlocked[0] ?? null;
  if (!hero) {
    return (
      <div className="side-card hero-card empty">
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
  const where = hero.state === "MARCHING" ? (arriving != null ? "Marching here" : "Marching")
    : hero.state === "WOUNDED" ? "Wounded"
    : hero.stationedCityName ? `at ${hero.stationedCityName}` : "Unassigned";

  const run = (fn: () => Promise<any>) => async () => {
    setErr(""); setBusy(true);
    try { await fn(); onChanged(); } catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };

  return (
    <div className="side-card hero-card">
      <div className="hc-top">
        <div className="hc-portrait"><span>{HERO_GLYPH(hero)}</span><span className="hc-lv">Lv {hero.level}</span></div>
        <div className="hc-id">
          <div className="hc-name">{hero.name}</div>
          <div className="hc-title muted">{titleCase(hero.race)} champion · {where}</div>
        </div>
        {hero.unspentAttributePoints > 0 && <span className="nav-pip">{hero.unspentAttributePoints}</span>}
      </div>
      <div className="hc-stat">
        <div className="hc-stat-row"><span>Experience</span><span>{xpPct}%</span></div>
        <div className="hc-bar"><i style={{ width: xpPct + "%" }} /></div>
      </div>
      {arriving != null
        ? <button className="btn" disabled>🚶 Arriving in {clock(arriving)}</button>
        : hereIdle
          ? <button className="btn ghost" disabled={busy} onClick={run(() => deassignHero(hero.id))}>Deassign from city</button>
          : unassigned && idle
            ? <button className="btn" disabled={busy} onClick={run(() => assignHero(hero.id, cityId))}>Assign to this city</button>
            : elsewhereIdle
              ? <button className="btn ghost" disabled={busy} onClick={run(() => deassignHero(hero.id))}>Deassign from {hero.stationedCityName}</button>
              : <button className="btn" disabled>Hero busy</button>}
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

function CityTab({ active, now, counts, setCounts, onBuild, onTrain, onCancel, onFinish, onFound }: {
  active: CityDetail; now: number; counts: Record<string, number>;
  setCounts: (c: Record<string, number>) => void;
  onBuild: (t: string) => void; onTrain: (t: string, c: number) => void;
  onCancel: (j: number) => void; onFinish: (j: number) => void; onFound: () => void;
}) {
  const [selectedBuilding, setSelectedBuilding] = useState<string | null>(null);
  const [showLibrary, setShowLibrary] = useState(false);
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
    WALL: "Fortify your city against attacks and invasions.",
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

      {/* primary action toolbar */}
      <div className="city-action-bar">
        <button className="cab-btn" onClick={() => setSelectedBuilding("BARRACKS")}><span className="cab-ico">⚔</span>Recruit</button>
        <button className="cab-btn" onClick={() => setShowMarket(true)}><span className="cab-ico">🔁</span>Trade</button>
      </div>

      {/* building detail drawer */}
      {selected && (
        <div className="bld-drawer-backdrop" onClick={() => setSelectedBuilding(null)}>
          <div className="bld-drawer" onClick={e => e.stopPropagation()}>
            <button className="bld-drawer-close" onClick={() => setSelectedBuilding(null)}>✕</button>
            <div className="bld-drawer-head">
              <span className="bld-art big" dangerouslySetInnerHTML={{ __html: buildingSvg(selected.type, selected.level) }} />
              <div>
                <h2>{titleCase(selected.type)}</h2>
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
                    <span className={"costitem" + lack(r.wheat, selected.cost[2])}>🌾 {fmt(selected.cost[2])}</span>
                    {popNeed > 0 && <span className={"costitem" + lack(freePop, popNeed)}>👥 {popNeed} pop</span>}
                    <span className="costitem">⏱ {clock(selected.seconds)}</span>
                  </div>
                  {selected.benefit && <div className="bld-benefit">✨ {selected.benefit}</div>}
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
                  {active.trainable.filter(u => u.from === selected.type).map(u => {
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
                  })}
                </div>
                {(selected.type === "BARRACKS" ? active.queues.BARRACKS : active.queues.HARBOR).length > 0 && (
                  <div className="train-queue">
                    <h4>In training</h4>
                    {(() => { const tq = selected.type === "BARRACKS" ? active.queues.BARRACKS : active.queues.HARBOR;
                      return tq.map((j, i) => {
                      const rem = remaining(j.finishAt, now);
                      const pct = rem != null ? Math.round((1 - rem / j.totalSeconds) * 100) : 0;
                      // rushable unless an earlier batch trains the SAME unit
                      const canRush = !tq.slice(0, i).some(x => x.label === j.label);
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
                )}
                {active.units.length > 0 && (
                  <p className="muted" style={{ marginTop: 10 }}>Garrison: {active.units.map(u => `${u.count}× ${titleCase(u.type)}`).join(" · ")}</p>
                )}
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
          </div>
        </div>
      )}

      {showLibrary && <LibraryPanel cityId={active.id} onClose={() => setShowLibrary(false)} />}
      {showMarket && <TradePanel cityId={active.id} onClose={() => setShowMarket(false)} />}
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
            <span className="cbart" dangerouslySetInnerHTML={{ __html: buildingSvg(j.label, j.toLevel || 1) }} />
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
  onSend: (units: Record<string, number>, heroId: number | null) => void;
}) {
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [heroId, setHeroId] = useState<number | null>(null);
  const [transportOk, setTransportOk] = useState(true);
  const send = () => {
    const units = Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
    if (Object.keys(units).length === 0 || !transportOk) return;
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
                <TravelPreview originCityId={originCityId} targetCityId={target.id} units={counts} heroId={heroId} onState={setTransportOk} />
                <button className="btn" disabled={!transportOk} onClick={send}>
                  {transportOk ? "⚔ Send attack" : "🚢 Need more transport"}</button>
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
