import { useEffect, useRef, useState } from "react";
import { getWorld, doColonize, doRaid, sendMessage } from "../api";
import type { WorldData, WorldIsland, WorldCity, UnitDto } from "../types";
import { TravelPreview } from "../movements";

const fmt = (n: number) => n >= 10000 ? (n / 1000).toFixed(1) + "k" : Math.floor(n).toString();
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

export default function WorldView({ activeCityId, myUnits, onChanged, setErr }: {
  activeCityId: number; myUnits: UnitDto[]; onChanged: () => void; setErr: (s: string) => void;
}) {
  const [data, setData] = useState<WorldData | null>(null);
  const [sel, setSel] = useState<WorldIsland | null>(null);
  const [selCity, setSelCity] = useState<WorldCity | null>(null);   // drives the player popup
  const [raidTarget, setRaidTarget] = useState<WorldCity | null>(null);
  const [raidCounts, setRaidCounts] = useState<Record<string, number>>({});
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
    scroller.current.scrollLeft = mine.px - scroller.current.clientWidth / 2;
    scroller.current.scrollTop = mine.py - scroller.current.clientHeight / 2;
  }
  useEffect(() => { if (data) setTimeout(centerOnMyCity, 30); }, [data]);

  if (!data) return <p className="muted">Charting the seas…</p>;

  const act = async (fn: () => Promise<any>) => {
    setErr("");
    try { await fn(); await load(); onChanged(); } catch (e: any) { setErr(e.message); }
  };
  const colonize = (islandId: number, slot: number) => act(() => doColonize(activeCityId, islandId, slot));

  // cities belonging to the same owner as the clicked city (across all islands)
  const playerCities = (pid: number | null) =>
    pid == null ? [] : data.islands.flatMap(i => i.cities).filter(c => c.playerId === pid);
  const worldPlayer = (pid: number | null) => data.players.find(p => p.id === pid) || null;

  function openRaid(c: WorldCity) {
    setRaidCounts({});
    setRaidTarget(c);
  }
  function sendRaid() {
    if (!raidTarget) return;
    const units = Object.fromEntries(Object.entries(raidCounts).filter(([, n]) => n > 0));
    if (Object.keys(units).length === 0) { setErr("Select at least one unit"); return; }
    act(() => doRaid(activeCityId, raidTarget.id, units));
    setRaidTarget(null); setSelCity(null);
  }
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
            const n = isl.cities.length;
            const size = 130 + n * 16;
            const mine = isl.cities.some(c => c.faction === "self");
            return (
              <div className="island" key={isl.id} style={{ left: isl.px, top: isl.py, width: size, height: size }}
                onClick={() => { if (!wasDragged()) setSel(isl); }} title={isl.name}>
                <div className="island-land" style={{ borderRadius: blobRadius(isl.id) }}>
                  {mine && <span className="island-star">★</span>}
                  {isl.cities.map(c => {
                    const a = (c.slot / 10) * 2 * Math.PI - Math.PI / 2;
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

      {/* island plot list (colonise empty plots) */}
      {sel && (
        <div className="modal-backdrop" onClick={() => setSel(null)}>
          <div className="modal-window" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{sel.name}</h2>
              <button className="modal-close" onClick={() => setSel(null)}>✕</button>
            </div>
            <div className="modal-body">
              <table>
                <thead><tr><th>Plot</th><th>City</th><th>Owner</th><th>Points</th><th></th></tr></thead>
                <tbody>
                  {Array.from({ length: 10 }).map((_, slot) => {
                    const c = sel.cities.find(x => x.slot === slot);
                    if (!c) return (
                      <tr key={slot}><td>{slot + 1}</td><td className="muted">empty plot</td><td></td><td></td>
                        <td><button className="btn ghost" onClick={() => colonize(sel.id, slot)}>Colonise</button></td></tr>
                    );
                    return (
                      <tr key={slot}>
                        <td>{slot + 1}</td>
                        <td className={"faction-" + c.faction}><a style={{ cursor: "pointer" }} onClick={() => { setSel(null); setSelCity(c); }}>{c.name}</a></td>
                        <td className="muted">{c.owner}</td>
                        <td>{fmt(c.points)}</td>
                        <td>{c.faction !== "self" && c.faction !== "ally" &&
                          <button className="btn" onClick={() => { setSel(null); openRaid(c); setSelCity(c); }}>Raid</button>}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
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
                            <button className="btn" onClick={() => openRaid(c)}>⚔ Raid</button>}</td>
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
              <h2>Raid {raidTarget.name}</h2>
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
                    <TravelPreview originCityId={activeCityId} targetCityId={raidTarget.id} units={raidCounts} />
                    <button className="btn" onClick={sendRaid}>⚔ Send raid</button>
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
