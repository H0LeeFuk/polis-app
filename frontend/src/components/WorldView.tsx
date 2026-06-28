import { useEffect, useRef, useState } from "react";
import { getWorld, getIslandSlots, doAttack, sendMessage } from "../api";
import type { WorldData, WorldIsland, WorldCity, UnitDto, Hero, IslandSlots } from "../types";
import { TravelPreview, HeroPicker } from "../movements";
import { ResourceIslandModal } from "./NodePanel";
import { FoundCityModal } from "./FoundCity";
import BanditCampModal from "./BanditCampModal";

const fmt = (n: number) => n >= 10000 ? (n / 1000).toFixed(1) + "k" : Math.floor(n).toString();
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const SLOTS_PER_ISLAND = 12;
// extra sea around the islands so the map can be panned past their bounds
const PAD_X = 520, PAD_Y = 360;

export default function WorldView({ activeCityId, myUnits, heroes, myPlayerId, onChanged, setErr }: {
  activeCityId: number; myUnits: UnitDto[]; heroes: Hero[]; myPlayerId: number; onChanged: () => void; setErr: (s: string) => void;
}) {
  const [data, setData] = useState<WorldData | null>(null);
  const [sel, setSel] = useState<WorldIsland | null>(null);
  const [slots, setSlots] = useState<IslandSlots | null>(null);
  const [foundSlot, setFoundSlot] = useState<{ islandId: number; islandName: string; slotIndex: number } | null>(null);
  const [nodeIsland, setNodeIsland] = useState<WorldIsland | null>(null);
  const [banditIsland, setBanditIsland] = useState<WorldIsland | null>(null);
  const [selCity, setSelCity] = useState<WorldCity | null>(null);   // drives the player popup
  const [raidTarget, setRaidTarget] = useState<WorldCity | null>(null);
  const [raidCounts, setRaidCounts] = useState<Record<string, number>>({});
  const [raidHeroId, setRaidHeroId] = useState<number | null>(null);
  const [msgTo, setMsgTo] = useState<{ id: number; name: string } | null>(null);
  const [msgBody, setMsgBody] = useState("");
  const scroller = useRef<HTMLDivElement>(null);
  const drag = useRef<{ x: number; y: number; sl: number; st: number; moved: boolean } | null>(null);

  async function load() { try { setData(await getWorld()); } catch (e: any) { setErr(e.message); } }
  useEffect(() => { load(); }, []);

  function centerOnMyCity() {
    if (!data || !scroller.current) return;
    const mine = data.islands.find(i => i.cities.some(c => c.faction === "self"));
    if (!mine) return;
    scroller.current.scrollLeft = mine.px + PAD_X - scroller.current.clientWidth / 2;
    scroller.current.scrollTop = mine.py + PAD_Y - scroller.current.clientHeight / 2;
  }
  useEffect(() => { if (data) setTimeout(centerOnMyCity, 30); }, [data]);

  if (!data) return <p className="muted">Charting the seas…</p>;

  const act = async (fn: () => Promise<any>) => {
    setErr("");
    try { await fn(); await load(); onChanged(); } catch (e: any) { setErr(e.message); }
  };
  // load the 12-slot occupancy whenever an island modal opens
  const loadSlots = (islandId: number) => getIslandSlots(islandId).then(setSlots).catch((e: any) => setErr(e.message));
  function openIsland(isl: WorldIsland) { setSel(isl); setSlots(null); loadSlots(isl.id); }

  // cities belonging to the same owner as the clicked city (across all islands)
  const playerCities = (pid: number | null) =>
    pid == null ? [] : data.islands.flatMap(i => i.cities).filter(c => c.playerId === pid);
  const worldPlayer = (pid: number | null) => data.players.find(p => p.id === pid) || null;

  function openRaid(c: WorldCity) {
    setRaidCounts({});
    setRaidHeroId(null);
    setRaidTarget(c);
  }
  function sendRaid() {
    if (!raidTarget) return;
    const units = Object.fromEntries(Object.entries(raidCounts).filter(([, n]) => n > 0));
    if (Object.keys(units).length === 0) { setErr("Select at least one unit"); return; }
    act(() => doAttack(activeCityId, raidTarget.id, units, raidHeroId));
    setRaidTarget(null); setSelCity(null);
  }
  // heroes that can join: unlocked + idle in the city we attack from
  const heroesHere = heroes.filter(h => h.unlocked && h.state === "IDLE" && h.stationedCityId === activeCityId);
  async function doSendMessage() {
    if (!msgTo || !msgBody.trim()) return;
    setErr("");
    try { await sendMessage(msgTo.id, msgBody.trim()); setMsgTo(null); setMsgBody(""); }
    catch (e: any) { setErr(e.message); }
  }

  // drag-to-pan
  const onDown = (e: React.MouseEvent) => {
    if (e.button !== 0 || !scroller.current) return;
    drag.current = { x: e.clientX, y: e.clientY, sl: scroller.current.scrollLeft, st: scroller.current.scrollTop, moved: false };
  };
  const onMove = (e: React.MouseEvent) => {
    if (!drag.current || !scroller.current) return;
    const dx = e.clientX - drag.current.x, dy = e.clientY - drag.current.y;
    if (Math.abs(dx) + Math.abs(dy) > 4) drag.current.moved = true;
    scroller.current.scrollLeft = drag.current.sl - dx;
    scroller.current.scrollTop = drag.current.st - dy;
  };
  const onUp = () => { setTimeout(() => { drag.current = null; }, 0); };
  const wasDragged = () => drag.current?.moved === true;

  const owner = worldPlayer(selCity?.playerId ?? null);
  const ownedCities = selCity ? playerCities(selCity.playerId) : [];

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", position: "relative" }}>
      <button className="centerbtn" onClick={centerOnMyCity}>⌖ Center on my city</button>
      <div className="world" ref={scroller} style={{ flex: 1, minHeight: 0 }}
        onMouseDown={onDown} onMouseMove={onMove} onMouseUp={onUp} onMouseLeave={onUp}>
        <div className="wspace">
          {data.islands.map(isl => {
            if (isl.resource){
              const size = 110;
              return (
                <div className="island resource-island" key={isl.id} style={{ left: isl.px + PAD_X, top: isl.py + PAD_Y, width: size, height: size }}
                  onClick={() => { if (!wasDragged()) setNodeIsland(isl); }} title={isl.name + " — resource nodes"}>
                  <div className="island-land sanctuary" style={{ borderRadius: blobRadius(isl.id) }}>
                    <span className="sanctuary-ico">⛩</span>
                  </div>
                  <div className="nm">{isl.name}</div>
                </div>
              );
            }
            const n = isl.cities.length;
            const size = 130 + n * 16;
            const mine = isl.cities.some(c => c.faction === "self");
            const free = SLOTS_PER_ISLAND - n;
            return (
              <div className="island" key={isl.id} style={{ left: isl.px + PAD_X, top: isl.py + PAD_Y, width: size, height: size }}
                onClick={() => { if (!wasDragged()) openIsland(isl); }} title={`${isl.name} — ${free} free plot${free === 1 ? "" : "s"}`}>
                <div className="island-land" style={{ borderRadius: blobRadius(isl.id) }}>
                  {mine && <span className="island-star">★</span>}
                  {/* bandit camp — only on your home island */}
                  {mine && <button className="bandit-camp-ico" title="Bandit Camp"
                    onClick={(e) => { e.stopPropagation(); if (!wasDragged()) setBanditIsland(isl); }}>🏴‍☠️</button>}
                  {/* one marker per empty plot so every free city slot is visible on the map */}
                  {Array.from({ length: SLOTS_PER_ISLAND }).map((_, slot) => {
                    if (isl.cities.some(c => c.slot === slot)) return null;
                    const a = (slot / SLOTS_PER_ISLAND) * 2 * Math.PI - Math.PI / 2;
                    const rad = size * 0.33;
                    return <span key={"e" + slot} className="wslot-empty"
                      style={{ left: `calc(50% + ${Math.cos(a) * rad}px)`, top: `calc(50% + ${Math.sin(a) * rad}px)` }}
                      title={`Empty plot ${slot + 1} — found a city here`}
                      onClick={(e) => { e.stopPropagation(); if (!wasDragged()) setFoundSlot({ islandId: isl.id, islandName: isl.name, slotIndex: slot }); }}>🏛</span>;
                  })}
                  {isl.cities.map(c => {
                    const a = (c.slot / SLOTS_PER_ISLAND) * 2 * Math.PI - Math.PI / 2;
                    const rad = size * 0.33;
                    return (
                      <span key={c.slot} className={"wcity faction-bg-" + c.faction}
                        style={{ left: `calc(50% + ${Math.cos(a) * rad}px)`, top: `calc(50% + ${Math.sin(a) * rad}px)` }}
                        title={`${c.name} — ${c.owner}`}
                        onClick={(e) => { e.stopPropagation(); if (!wasDragged()) setSelCity(c); }} />
                    );
                  })}
                </div>
                <div className="nm">{isl.name}</div>
              </div>
            );
          })}
        </div>
      </div>

      {/* bandit camp */}
      {banditIsland && (
        <BanditCampModal islandId={banditIsland.id} activeCityId={activeCityId}
          cityOnIsland={banditIsland.cities.some(c => c.id === activeCityId)} myUnits={myUnits}
          onClose={() => setBanditIsland(null)} onChanged={() => { load(); onChanged(); }} setErr={setErr} />
      )}

      {/* resource island → node list */}
      {nodeIsland && (
        <ResourceIslandModal islandId={nodeIsland.id} islandName={nodeIsland.name}
          ctx={{ myPlayerId, activeCityId, myUnits, heroes: heroesHere, setErr, onChanged: () => { load(); onChanged(); } }}
          onClose={() => setNodeIsland(null)} />
      )}

      {/* island plot list — 12 slots, found a city on empty settleable plots */}
      {sel && (
        <div className="modal-backdrop" onClick={() => setSel(null)}>
          <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(560px,100%)" }}>
            <div className="modal-header">
              <h2>{sel.name} <small className="muted">Cities: {slots ? slots.occupied : "…"} / {SLOTS_PER_ISLAND}</small></h2>
              <button className="modal-close" onClick={() => setSel(null)}>✕</button>
            </div>
            <div className="modal-body">
              {!slots ? <p className="muted">Surveying the island…</p> : (
                <table>
                  <thead><tr><th>Plot</th><th>City</th><th>Owner</th><th>Race</th><th></th></tr></thead>
                  <tbody>
                    {slots.slots.map(s => {
                      if (s.status === "EMPTY") return (
                        <tr key={s.slotIndex}>
                          <td>{s.slotIndex + 1}</td>
                          <td className={"muted" + (s.canSettle ? " slot-open" : "")}>empty plot</td>
                          <td></td><td></td>
                          <td>{s.canSettle
                            ? <button className="btn" onClick={() => { setFoundSlot({ islandId: sel.id, islandName: sel.name, slotIndex: s.slotIndex }); }}>🏛 Found city</button>
                            : <small className="muted" title={s.reason ?? ""}>{s.reason ?? "—"}</small>}</td>
                        </tr>
                      );
                      const wc = sel.cities.find(x => x.id === s.cityId) ?? null;
                      return (
                        <tr key={s.slotIndex}>
                          <td>{s.slotIndex + 1}</td>
                          <td className={"faction-" + (s.faction ?? "")}>
                            {wc ? <a style={{ cursor: "pointer" }} onClick={() => { setSel(null); setSelCity(wc); }}>{s.cityName}</a> : s.cityName}
                          </td>
                          <td className="muted">{s.ownerName}{s.alliance ? ` · ${s.alliance}` : ""}</td>
                          <td title={s.race?.name}>{s.race ? `${s.race.icon} ${s.race.name}` : "—"}</td>
                          <td>{wc && s.faction !== "self" && s.faction !== "ally" &&
                            <button className="btn" onClick={() => { setSel(null); openRaid(wc); setSelCity(wc); }}>Attack</button>}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      )}

      {/* found-city stepper (send hero → choose race) */}
      {foundSlot && (
        <FoundCityModal islandId={foundSlot.islandId} islandName={foundSlot.islandName} slotIndex={foundSlot.slotIndex}
          heroes={heroes} fromCityId={activeCityId} fromCityName={null}
          onClose={() => setFoundSlot(null)} setErr={setErr}
          onChanged={() => { setSel(null); load(); onChanged(); }} />
      )}

      {/* player popup (clicked a city) */}
      {selCity && (
        <div className="modal-backdrop" onClick={() => setSelCity(null)}>
          <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(520px,100%)" }}>
            <div className="modal-header">
              <h2>{selCity.owner}</h2>
              <button className="modal-close" onClick={() => setSelCity(null)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="popup-panel">
                {owner && (
                  <div className="popup-grid">
                    <div><strong>Player</strong><span>{owner.name}</span></div>
                    <div><strong>Level</strong><span>{owner.level}</span></div>
                    <div><strong>Combat points</strong><span>{fmt(owner.combatPoints)}</span></div>
                  </div>
                )}
                <h3 style={{ margin: "4px 0 0" }}>Cities</h3>
                <table>
                  <thead><tr><th>City</th><th>Island</th><th>Points</th><th></th></tr></thead>
                  <tbody>
                    {(ownedCities.length ? ownedCities : [selCity]).map(c => {
                      const isl = data.islands.find(i => i.cities.some(x => x.id === c.id));
                      return (
                        <tr key={c.id}>
                          <td className={"faction-" + c.faction}>{c.name}</td>
                          <td className="muted">{isl?.name || "—"}</td>
                          <td>{fmt(c.points)}</td>
                          <td>{c.faction !== "self" && c.faction !== "ally" &&
                            <button className="btn" onClick={() => openRaid(c)}>⚔ Attack</button>}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
                {selCity.playerId != null && selCity.faction !== "self" && (
                  <button className="btn ghost" onClick={() => { setMsgTo({ id: selCity.playerId!, name: selCity.owner }); }}>✉ Message {selCity.owner}</button>
                )}
                {selCity.faction === "self" && <p className="muted">This is your city. Switch to it from the city selector.</p>}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* raid troop picker */}
      {raidTarget && (
        <div className="modal-backdrop" onClick={() => setRaidTarget(null)}>
          <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(460px,100%)" }}>
            <div className="modal-header">
              <h2>Attack {raidTarget.name}</h2>
              <button className="modal-close" onClick={() => setRaidTarget(null)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="popup-panel">
                {myUnits.length === 0 ? (
                  <p className="muted">No troops in your active city. Train some in the Barracks or Harbor first.</p>
                ) : (
                  <>
                    <p className="muted">Select troops to send from your active city:</p>
                    {myUnits.map(u => (
                      <div key={u.type} className="raid-row">
                        <span>{titleCase(u.type)} <small className="muted">({u.count} available)</small></span>
                        <input type="number" min={0} max={u.count} value={raidCounts[u.type] || 0}
                          onChange={e => setRaidCounts({ ...raidCounts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
                      </div>
                    ))}
                    <HeroPicker heroes={heroesHere} value={raidHeroId} onChange={setRaidHeroId} />
                    <TravelPreview originCityId={activeCityId} targetCityId={raidTarget.id} units={raidCounts} heroId={raidHeroId} />
                    <button className="btn" onClick={sendRaid}>⚔ Send attack</button>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* message compose */}
      {msgTo && (
        <div className="modal-backdrop" onClick={() => setMsgTo(null)}>
          <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(460px,100%)" }}>
            <div className="modal-header">
              <h2>Message {msgTo.name}</h2>
              <button className="modal-close" onClick={() => setMsgTo(null)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="popup-panel">
                <textarea className="msg-input" rows={5} value={msgBody} placeholder="Write your message…"
                  onChange={e => setMsgBody(e.target.value)} />
                <button className="btn" disabled={!msgBody.trim()} onClick={doSendMessage}>Send</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// Deterministic organic blob shape per island id (8-point border-radius).
function blobRadius(id: number): string {
  const v = (k: number) => 42 + ((id * 9301 + k * 49297) % 18);
  return `${v(1)}% ${v(2)}% ${v(3)}% ${v(4)}% / ${v(5)}% ${v(6)}% ${v(7)}% ${v(8)}%`;
}
