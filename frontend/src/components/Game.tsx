import { useEffect, useRef, useState } from "react";
import {
  getState, doBuild, doTrain, doResearch, doCancel, doRename,
} from "../api";
import type { GameState, CityDetail } from "../types";
import { buildingSvg, constructionSvg } from "../buildings";
import WorldView from "./WorldView";
import Rankings from "./Rankings";

const fmt = (n: number) => n >= 10000 ? (n / 1000).toFixed(n >= 100000 ? 0 : 1) + "k" : Math.floor(n).toString();
const RES_GLYPH: Record<string, string> = { wood: "🪵", stone: "🪨", silver: "🪙", favor: "✨" };

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
  const polling = useRef<number>();

  async function refresh(cityId = activeCityId) {
    try {
      const s = await getState(cityId);
      setState(s);
      if (cityId === undefined) setActiveCityId(s.active.id);
    } catch (e: any) {
      if (String(e.message).includes("401")) onLogout(); else setErr(e.message);
    }
  }

  useEffect(() => { refresh(); }, []);
  useEffect(() => {
    polling.current = window.setInterval(() => refresh(), 3000);
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => { clearInterval(polling.current); clearInterval(t); };
  }, [activeCityId]);

  if (!state) return <div className="app"><p className="muted">Loading the Aegean…</p></div>;
  const { player, cities, active } = state;

  const action = (fn: () => Promise<any>) => async () => {
    setErr("");
    try { await fn(); await refresh(); } catch (e: any) { setErr(e.message); }
  };
  const switchCity = (id: number) => { setActiveCityId(id); refresh(id); };
  const nextCity = () => {
    const ids = cities.map(c => c.id).sort((a, b) => a - b);
    if (ids.length < 2) return;
    const i = ids.indexOf(active.id);
    switchCity(ids[(i + 1) % ids.length]);
  };

  return (
    <div className="app">
      <div className="topbar">
        <div className="topbar-left">
          <div className="brand">POLIS</div>
          <div className="cityswitch">
            <button className={"cs-name" + (cities.length < 2 ? " solo" : "")}
              onClick={() => {
                const name = prompt("Rename this city", active.name);
                if (name && name.trim()) action(() => doRename(active.id, name.trim()))();
              }}>{active.name}</button>
            {cities.length > 1 && <button className="cs-next" title="Next city" onClick={nextCity}>❯</button>}
          </div>
        </div>

        <ResourceBar active={active} />

        <div className="topbar-right">
          <div className="who">Lv <b>{player.level}</b> · <b>{fmt(player.totalPoints)}</b> pts{player.alliance ? ` · ${player.alliance}` : ""}</div>
          <button className="logout" onClick={onLogout}>log out</button>
        </div>
      </div>

      {err && <div className="err">{err}</div>}

      <div className="view-toggle">
        <button className={"view-btn" + (tab === "city" ? " active" : "")} onClick={() => setTab("city")}>City View</button>
        <button className={"view-btn" + (tab === "world" ? " active" : "")} onClick={() => setTab("world")}>World View</button>
      </div>

      <div className="fullscreen-view">
        <div className="panel-actions">
          <button className="panel-action-btn" onClick={() => setModal("rankings")}>Rankings</button>
          <button className="panel-action-btn" onClick={() => setModal("profile")}>Profile</button>
          <button className="panel-action-btn" onClick={() => setModal("alliance")}>Alliance</button>
          <button className="panel-action-btn" onClick={() => setModal("messages")}>Messages</button>
        </div>
        {tab === "city" && <CityTab active={active} now={now} counts={counts} setCounts={setCounts}
          onBuild={(t) => action(() => doBuild(active.id, t))()}
          onTrain={(t, c) => action(() => doTrain(active.id, t, c))()}
          onResearch={(t) => action(() => doResearch(active.id, t))()}
          onCancel={(j) => action(() => doCancel(active.id, j))()} />}

        {tab === "world" && <WorldView activeCityId={active.id} onChanged={() => refresh()} setErr={setErr} />}
      </div>

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
          <p className="muted">Manage your profile, see stats, and track your progress in the Aegean.</p>
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
      {modal === "messages" && <Modal title="Messages" onClose={() => setModal(null)}>
        <div className="popup-panel">
          <h3>Inbox</h3>
          <p className="muted">No new messages. Your communications will appear here when you receive reports, diplomacy notes, or alerts.</p>
        </div>
      </Modal>}
    </div>
  );
}

function ResourceBar({ active }: { active: CityDetail }) {
  const r = active.resources;
  const chip = (k: string, v: number, sub?: string, full?: boolean) => (
    <div className={"chip" + (full ? " full" : "")} key={k}>
      <span className="glyph">{RES_GLYPH[k]}</span>
      <span className="v">{fmt(v)}<small>{sub}</small></span>
    </div>
  );
  return (
    <div className="resbar">
      {chip("wood", r.wood, `/${fmt(r.capacity)}`, r.wood >= r.capacity)}
      {chip("stone", r.stone, `/${fmt(r.capacity)}`, r.stone >= r.capacity)}
      {chip("silver", r.silver, `/${fmt(r.capacity)}`, r.silver >= r.capacity)}
      {active.god && chip("favor", r.favor, "favor")}
      <div className="chip"><span className="glyph">👥</span><span className="v">{active.pop}<small>{`${active.maxPop} pop`}</small></span></div>
    </div>
  );
}

function CityTab({ active, now, counts, setCounts, onBuild, onTrain, onResearch, onCancel }: {
  active: CityDetail; now: number; counts: Record<string, number>;
  setCounts: (c: Record<string, number>) => void;
  onBuild: (t: string) => void; onTrain: (t: string, c: number) => void;
  onResearch: (t: string) => void; onCancel: (j: number) => void;
}) {
  const [panel, setPanel] = useState<"build" | "train" | "research" | "status">("build");
  const [selectedBuilding, setSelectedBuilding] = useState<string | null>(null);
  const r = active.resources;
  const afford = (cost: number[]) => r.wood >= cost[0] && r.stone >= cost[1] && r.silver >= cost[2];
  const constructing = new Set(active.queues.BUILDING.map(j => j.label));

  const selected = active.buildings.find(b => b.type === selectedBuilding) || active.buildings[0] || null;
  const buildingInfo: Record<string, string> = {
    BARRACKS: "Train troops, house soldiers and strengthen your army.",
    HARBOR: "Build naval units and send raids across the sea.",
    ACADEMY: "Research upgrades, unlock units and boost your combat power.",
    WAREHOUSE: "Increase resource storage and protect your stockpile.",
    TEMPLE: "Gain favor from the gods and unlock divine blessings.",
    QUARRY: "Produce stone for construction and military upgrades.",
    FARM: "Grow food to support a larger population and faster growth.",
    MINE: "Extract silver for trade, army upkeep and city development.",
    GARRISON: "Hold troops and reinforce your city defenses.",
  };

  useEffect(() => {
    setSelectedBuilding(active.buildings[0]?.type ?? null);
  }, [active.buildings]);

  return (
    <div className="city-view">
      <div className="city-bg" />
      <div className="city-overlay">
        <div className="city-menu">
          <button className={"city-ring-btn" + (panel === "build" ? " active" : "")} onClick={() => setPanel("build")}>Buildings</button>
          <button className={"city-ring-btn" + (panel === "train" ? " active" : "")} onClick={() => setPanel("train")}>Troops</button>
          <button className={"city-ring-btn" + (panel === "research" ? " active" : "")} onClick={() => setPanel("research")}>Research</button>
          <button className={"city-ring-btn" + (panel === "status" ? " active" : "")} onClick={() => setPanel("status")}>City Status</button>
        </div>

        <div className="city-main">
          <div className="city-side">
            <div className="city-panel">
              <h2>City Command</h2>
              <div className="chip-row" style={{ display: "flex", flexWrap: "wrap", gap: "10px", marginTop: 10 }}>
                <div className="chip"><span className="glyph">🏛</span><span className="v">{active.capital ? "Capital" : "City"}<small>{titleCase(active.island)}</small></span></div>
                <div className="chip"><span className="glyph">👥</span><span className="v">{active.pop}/{active.maxPop}<small>Population</small></span></div>
                <div className="chip"><span className="glyph">🪵</span><span className="v">{fmt(r.wood)}<small>Wood</small></span></div>
                <div className="chip"><span className="glyph">🪨</span><span className="v">{fmt(r.stone)}<small>Stone</small></span></div>
                <div className="chip"><span className="glyph">🪙</span><span className="v">{fmt(r.silver)}<small>Silver</small></span></div>
                {active.god && <div className="chip"><span className="glyph">✨</span><span className="v">{fmt(r.favor)}<small>Favor</small></span></div>}
              </div>
            </div>
            <div className="city-panel">
              <h2>Construction</h2>
              <Queue title="Construction" jobs={active.queues.BUILDING} now={now} onCancel={onCancel} suffix={(j) => `→ Lv ${j.toLevel}`} />
            </div>
          </div>

          <div className="city-core">
            <div className="city-map-card">
              <div className="city-map-inner">
                <div className="city-map-title">
                  <div>
                    <h2>{titleCase(active.name)}</h2>
                    <small>{active.god ? `Blessed by ${titleCase(active.god)}` : "No god chosen yet"}</small>
                  </div>
                  <div className="pill">{active.capital ? "Capital" : "City"}</div>
                </div>
                <div className="city-map-splash">
                  <div className="city-map-feature">🏛</div>
                  <div className="city-map-feature city-feature-small">Barracks</div>
                  <div className="city-map-feature city-feature-small">Harbor</div>
                  <div className="city-map-feature city-feature-small">Academy</div>
                </div>
                <div className="city-map-legend">
                  <div className="city-summary">Wood<br /><strong>{fmt(r.wood)}</strong></div>
                  <div className="city-summary">Stone<br /><strong>{fmt(r.stone)}</strong></div>
                  <div className="city-summary">Silver<br /><strong>{fmt(r.silver)}</strong></div>
                  <div className="city-summary">Population<br /><strong>{active.pop}/{active.maxPop}</strong></div>
                </div>
              </div>
            </div>
          </div>

          <div className="city-side">
            {panel === "build" && (
              <div className="city-panel">
                <h2>Buildings</h2>
                <div className="grid">
                  {active.buildings.map(b => (
                    <div className={"card" + (selected?.type === b.type ? " selected" : "")} key={b.type} onClick={() => setSelectedBuilding(b.type)}>
                      <div className="svg" dangerouslySetInnerHTML={{ __html: constructing.has(b.type) ? constructionSvg() : buildingSvg(b.type, b.level) }} />
                      <h3>{titleCase(b.type)}</h3>
                      <div className="muted">Level {b.level}{b.atMax ? " (max)" : ""}</div>
                      {!b.atMax && <div className="cost">🪵 <b>{fmt(b.cost[0])}</b> · 🪨 <b>{fmt(b.cost[1])}</b> · 🪙 <b>{fmt(b.cost[2])}</b><br />⏱ {clock(b.seconds)}</div>}
                      <button className="btn" disabled>{selected?.type === b.type ? "Selected" : "View"}</button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {panel === "train" && (
              <div className="city-panel">
                <h2>Troops</h2>
                <div className="grid">
                  {active.trainable.map(u => (
                    <div className="card" key={u.type}>
                      <h3>{titleCase(u.type)}</h3>
                      <div className="muted">⚔ {u.atk} · 🛡 {u.def} · {u.kind === "SEA" ? "🌊" : "🏃"} {u.speed}{u.carry ? ` · 🎒${u.carry}` : ""}</div>
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
                {active.units.length > 0 && (
                  <p className="muted" style={{ marginTop: 10 }}>
                    Garrison: {active.units.map(u => `${u.count}× ${titleCase(u.type)}`).join(" · ")}
                  </p>
                )}
              </div>
            )}

            {panel === "research" && (
              <div className="city-panel">
                <h2>Academy research</h2>
                <div className="grid">
                  {active.research.map(rs => (
                    <div className="card" key={rs.type}>
                      <h3>{titleCase(rs.type)}</h3>
                      <div className="muted">Academy Lv {rs.req}+</div>
                      <div className="cost">🪵 {rs.cost[0]} · 🪨 {rs.cost[1]} · 🪙 {rs.cost[2]}</div>
                      <button className="btn" disabled={rs.done || !afford(rs.cost)} onClick={() => onResearch(rs.type)}>
                        {rs.done ? "✓ Researched" : "Research"}
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {panel === "status" && (
              <div className="city-panel">
                <h2>City status</h2>
                <div className="muted">Buildings, queues and movements around your city.</div>
                <Queue title="Barracks" jobs={active.queues.BARRACKS} now={now} onCancel={onCancel} suffix={(j) => `×${j.batch}`} />
                <Queue title="Harbour" jobs={active.queues.HARBOR} now={now} onCancel={onCancel} suffix={(j) => `×${j.batch}`} />
                {active.movements.length > 0 && (
                  <div style={{ marginTop: 10 }}>
                    <h3 style={{ marginBottom: 8 }}>Fleets & armies</h3>
                    {active.movements.map(m => (
                      <div className="qrow" key={m.id} style={{ display: "block" }}>
                        <div style={{ display: "flex", justifyContent: "space-between" }}>
                          <span>{phaseLabel(m.phase)} → <b>{m.target}</b></span>
                          <span className="pill">{etaLabel(m.arriveAt, now)}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="city-footer">
          <span className="muted">Tap one of the city menus to see buildings, troops, research and status around your city.</span>
        </div>
      </div>
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
