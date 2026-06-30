import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { getWorld, getIslandSlots, doAttack, doSupport, sendMessage, getWorldState, getColossi, launchSpy, getMyMovements } from "../api";
import type { WorldData, WorldIsland, WorldCity, UnitDto, Hero, IslandSlots, WonderDto, ColossusDto, Movement } from "../types";
import MovementLines from "./MovementLines";
import { type MapLinePrefs, loadLinePrefs, saveLinePrefs, NEUTRAL_COLOR, MOVE_TYPES } from "../mapMovementPrefs";
import { TravelPreview, HeroPicker } from "../movements";
import { ResourceIslandModal } from "./NodePanel";
import { WonderModal } from "./WondersPanel";
import { FoundCityModal } from "./FoundCity";
import ColossusPanel from "./ColossusPanel";

const fmt = (n: number) => n >= 10000 ? (n / 1000).toFixed(1) + "k" : Math.floor(n).toString();
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const SLOTS_PER_ISLAND = 12;
const WONDER_ICON: Record<string, string> = { LIGHTHOUSE: "🗼", COLOSSUS: "🗽", SANCTUM: "🏯" };
// extra sea around the islands so the map can be panned past their bounds
const PAD_X = 520, PAD_Y = 360;
// every player island renders at the same fixed size (must match MovementLines.cityPos geometry)
export const ISLAND_SIZE = 150;
// fallback world centre (only used if there are no islands to derive a centroid from)
const WORLD_CENTER = 2900;
const TIER_ROMAN = ["I", "II", "III"];

export default function WorldView({ activeCityId, myUnits, heroes, myPlayerId, onChanged, setErr }: {
  activeCityId: number; myUnits: UnitDto[]; heroes: Hero[]; myPlayerId: number; onChanged: () => void; setErr: (s: string) => void;
}) {
  const [data, setData] = useState<WorldData | null>(null);
  const [sel, setSel] = useState<WorldIsland | null>(null);
  const [slots, setSlots] = useState<IslandSlots | null>(null);
  const [foundSlot, setFoundSlot] = useState<{ islandId: number; islandName: string; slotIndex: number } | null>(null);
  const [nodeIsland, setNodeIsland] = useState<WorldIsland | null>(null);
  const [wonders, setWonders] = useState<WonderDto[]>([]);
  const [wonderSel, setWonderSel] = useState<WonderDto | null>(null);
  const [colossi, setColossi] = useState<ColossusDto[]>([]);
  const [colossusSel, setColossusSel] = useState<number | null>(null);
  const [spyNote, setSpyNote] = useState("");
  const [movements, setMovements] = useState<Movement[]>([]);
  const [linePrefs, setLinePrefs] = useState<MapLinePrefs>(loadLinePrefs);
  const [showLineSettings, setShowLineSettings] = useState(false);
  const [selCity, setSelCity] = useState<WorldCity | null>(null);   // drives the player popup
  const [raidTarget, setRaidTarget] = useState<WorldCity | null>(null);
  const [raidCounts, setRaidCounts] = useState<Record<string, number>>({});
  const [raidHeroId, setRaidHeroId] = useState<number | null>(null);
  const [raidSiege, setRaidSiege] = useState(false);   // "Lay siege" intent (requires a hero + a Defense ship)
  const [supportTarget, setSupportTarget] = useState<WorldCity | null>(null);
  const [supportCounts, setSupportCounts] = useState<Record<string, number>>({});
  const [msgTo, setMsgTo] = useState<{ id: number; name: string } | null>(null);
  const [msgBody, setMsgBody] = useState("");
  const scroller = useRef<HTMLDivElement>(null);
  const drag = useRef<{ x: number; y: number; sl: number; st: number; moved: boolean } | null>(null);
  const [scale, setScale] = useState(1);
  const scaleRef = useRef(1);
  // pending cursor-anchor for the next zoom: the content point (in UNSCALED map px) that must stay
  // under the cursor, plus the cursor's viewport offset. Consumed in a layout effect AFTER the DOM
  // has resized — doing it in rAF raced React's commit and the browser clamped scroll → jumpy zoom.
  const zoomAnchor = useRef<{ mx: number; my: number; cx: number; cy: number } | null>(null);
  const WSPACE = 6300;   // matches .wspace intrinsic size in styles.css (world center 2900 + outer 2800 + pad)
  const MIN_SCALE = 0.4, MAX_SCALE = 2.5;

  // scroll wheel zooms (anchored on the cursor) instead of panning; drag still pans.
  useEffect(() => {
    const el = scroller.current; if (!el) return;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      const rect = el.getBoundingClientRect();
      const cx = e.clientX - rect.left, cy = e.clientY - rect.top;
      const cur = scaleRef.current;
      // proportional, smooth zoom: factor scales with wheel magnitude (handles trackpad/momentum and
      // line-vs-pixel deltaMode), so one notch and a fast flick both feel right instead of fixed steps.
      const unit = e.deltaMode === 1 ? 16 : e.deltaMode === 2 ? el.clientHeight : 1;   // lines/pages → px
      const dy = Math.max(-120, Math.min(120, e.deltaY * unit));   // clamp so a big jump can't lurch
      const next = Math.min(MAX_SCALE, Math.max(MIN_SCALE, cur * Math.exp(-dy * 0.0022)));
      if (Math.abs(next - cur) < 1e-4) return;
      // the map point currently under the cursor, in unscaled px — pin it there after the resize.
      zoomAnchor.current = { mx: (el.scrollLeft + cx) / cur, my: (el.scrollTop + cy) / cur, cx, cy };
      scaleRef.current = next;
      setScale(next);
    };
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  }, [data]);   // scroller only mounts once data loads (component early-returns while null)

  // reposition AFTER the .wzoom box has resized to the new scale (layout effect = post-DOM-mutation,
  // pre-paint) so scrollLeft isn't clamped against the old, smaller content size. Keeps the cursor's
  // map point fixed under the pointer → no drift, no jump.
  useLayoutEffect(() => {
    const a = zoomAnchor.current; const el = scroller.current;
    if (!a || !el) return;
    zoomAnchor.current = null;
    el.scrollLeft = a.mx * scale - a.cx;
    el.scrollTop = a.my * scale - a.cy;
  }, [scale]);

  async function load() {
    try { setData(await getWorld()); } catch (e: any) { setErr(e.message); }
    getWorldState().then(s => setWonders(s.wonders)).catch(() => {});
    getColossi().then(setColossi).catch(() => {});
  }
  useEffect(() => { load(); }, []);
  // colossi roam live — refresh their position/health every 10s while the map is open
  useEffect(() => { const t = setInterval(() => getColossi().then(setColossi).catch(() => {}), 10000); return () => clearInterval(t); }, []);
  // active movements for the live map lines — poll often (marches can resolve in seconds under a
  // fast TIME_SCALE); markers glide client-side between polls.
  useEffect(() => {
    const fetchMoves = () => getMyMovements().then(d => setMovements(d.movements)).catch(() => {});
    fetchMoves();
    const t = setInterval(fetchMoves, 4000);
    return () => clearInterval(t);
  }, []);
  const updatePrefs = (p: MapLinePrefs) => { setLinePrefs(p); saveLinePrefs(p); };
  // ownership changes (conquest, new foundings, others' moves) — refresh the map every 10s so a
  // city you just conquered shows up without a manual reload. Does NOT re-center (see below).
  useEffect(() => { const t = setInterval(() => getWorld().then(setData).catch(() => {}), 10000); return () => clearInterval(t); }, []);
  const wonderByIsland = new Map(wonders.map(w => [w.islandId, w]));

  function centerOnMyCity() {
    if (!data || !scroller.current) return;
    // center on the island holding the currently SELECTED city, not just any of mine
    const mine = data.islands.find(i => i.cities.some(c => c.id === activeCityId))
      ?? data.islands.find(i => i.cities.some(c => c.faction === "self"));
    if (!mine) return;
    const sc = scaleRef.current;
    scroller.current.scrollLeft = (mine.px + PAD_X) * sc - scroller.current.clientWidth / 2;
    scroller.current.scrollTop = (mine.py + PAD_Y) * sc - scroller.current.clientHeight / 2;
  }
  // center once when the map first loads, and again whenever the SELECTED city changes — but NOT on
  // every background data refresh (that would yank the map out from under the player every 10s).
  const didCenter = useRef(false);
  useEffect(() => { if (data && !didCenter.current) { didCenter.current = true; setTimeout(centerOnMyCity, 30); } }, [data]);
  useEffect(() => { if (data) setTimeout(centerOnMyCity, 30); }, [activeCityId]);

  if (!data) return <p className="muted">Charting the seas…</p>;

  // Data-driven tier rings: centre = centroid of all islands; each ring's radius encloses its tier's
  // islands (+ a margin for island body/label). This stays correct for ANY backend geometry/seed —
  // the rings always sit OUTSIDE their tier's islands instead of relying on hardcoded radii.
  const allIsl = data.islands;
  const ringCx = allIsl.length ? allIsl.reduce((s, i) => s + i.px, 0) / allIsl.length : WORLD_CENTER;
  const ringCy = allIsl.length ? allIsl.reduce((s, i) => s + i.py, 0) / allIsl.length : WORLD_CENTER;
  const RING_MARGIN = 190;   // largest island half-width (~161) + name label headroom
  // Radial extent (min/max distance from the centroid) of each tier's islands. Tiers are nested:
  // T1 outer, T2 mid, T3 core. A boundary line must sit in the CLEAR GAP between two tiers' islands —
  // at the midpoint of that gap — so no island ever renders on a division line. The outermost line
  // (T1) has no tier beyond it, so it sits a margin past T1's outermost island.
  const ext: Record<number, { min: number; max: number }> = {};
  for (const t of [1, 2, 3]) {
    const rs = allIsl.filter(i => (i.tier ?? 0) === t)
      .map(i => Math.hypot(i.px - ringCx, i.py - ringCy));
    if (rs.length) ext[t] = { min: Math.min(...rs), max: Math.max(...rs) };
  }
  const mid = (a: number, b: number) => (a + b) / 2;   // midpoint of the gap between two tiers
  let tierRings = [1, 2, 3].map(t => {
    if (!ext[t]) return { tier: t, r: 0 };
    let r: number;
    if (t === 1) r = ext[1].max + RING_MARGIN;                 // outer edge of the world
    else {
      const outer = ext[t - 1];                                 // the next tier OUT from this one
      r = outer ? mid(ext[t].max, outer.min) : ext[t].max + RING_MARGIN;
    }
    return { tier: t, r };
  }).filter(x => x.r > 0);
  // Fallback (older backend not sending `tier`, or no tier data): split islands into three distance
  // bands from the centroid so the three tier lines still draw and always enclose their islands.
  if (tierRings.length === 0 && allIsl.length) {
    const dists = allIsl.map(i => Math.hypot(i.px - ringCx, i.py - ringCy)).sort((a, b) => a - b);
    const q = (p: number) => dists[Math.min(dists.length - 1, Math.floor(p * (dists.length - 1)))];
    tierRings = [1, 2, 3].map(t => ({ tier: t, r: q(t / 3) + RING_MARGIN }));
  }

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
    setRaidSiege(false);
    setRaidTarget(c);
  }
  function sendRaid() {
    if (!raidTarget) return;
    const units = Object.fromEntries(Object.entries(raidCounts).filter(([, n]) => n > 0));
    // a lone hero may attack on its own — only block a truly empty order
    if (Object.keys(units).length === 0 && raidHeroId == null) { setErr("Select at least one unit or a hero"); return; }
    if (raidSiege && raidHeroId == null) { setErr("A siege must be led by a hero — pick one above"); return; }
    act(() => doAttack(activeCityId, raidTarget.id, units, raidHeroId, raidSiege ? "SIEGE" : undefined));
    setRaidTarget(null); setSelCity(null);
  }
  function openSupport(c: WorldCity) { setSupportCounts({}); setSupportTarget(c); }
  function sendSupport() {
    if (!supportTarget) return;
    const units = Object.fromEntries(Object.entries(supportCounts).filter(([, n]) => n > 0));
    if (Object.keys(units).length === 0) { setErr("Select at least one unit to send"); return; }
    act(() => doSupport(activeCityId, supportTarget.id, units));
    setSupportTarget(null); setSelCity(null);
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
      <button className="linesbtn" title="Movement lines" onClick={() => setShowLineSettings(s => !s)}>⚹ Lines</button>
      {showLineSettings && (
        <div className="lines-settings" onClick={e => e.stopPropagation()}>
          <div className="ls-head">
            <b>Map movement lines</b>
            <button className="modal-close" onClick={() => setShowLineSettings(false)}>✕</button>
          </div>
          <label className="ls-row"><span>Show lines</span>
            <input type="checkbox" checked={linePrefs.enabled} onChange={e => updatePrefs({ ...linePrefs, enabled: e.target.checked })} /></label>
          <label className="ls-row"><span>Resting opacity</span>
            <input type="range" min={0.1} max={1} step={0.05} value={linePrefs.opacity}
              onChange={e => updatePrefs({ ...linePrefs, opacity: +e.target.value })} /></label>
          <div className="ls-sub">Colors by type</div>
          {MOVE_TYPES.map(mt => (
            <label className="ls-row" key={mt.type}><span>{mt.icon} {mt.label}</span>
              <span className="ls-color">
                <input type="color" value={linePrefs.colors[mt.type] || NEUTRAL_COLOR}
                  onChange={e => updatePrefs({ ...linePrefs, colors: { ...linePrefs.colors, [mt.type]: e.target.value } })} />
                {linePrefs.colors[mt.type] && <button className="btn ghost tiny" onClick={() => {
                  const c = { ...linePrefs.colors }; delete c[mt.type]; updatePrefs({ ...linePrefs, colors: c });
                }}>reset</button>}
              </span></label>
          ))}
          <p className="muted" style={{ fontSize: 11, margin: "6px 0 0" }}>Incoming attacks always pulse red. Hover a line to sharpen it.</p>
        </div>
      )}
      <div className="world" ref={scroller} style={{ flex: 1, minHeight: 0 }}
        onMouseDown={onDown} onMouseMove={onMove} onMouseUp={onUp} onMouseLeave={onUp}>
        <div className="wzoom" style={{ position: "relative", width: WSPACE * scale, height: WSPACE * scale }}>
        <div className="wspace" style={{ transform: `scale(${scale})`, transformOrigin: "0 0" }}>
          {/* tier zones — concentric rings derived from the live island positions (always enclose them) */}
          {tierRings.slice().reverse().map(({ tier, r }) => (
            <div key={"tz" + tier} className={"tier-zone tier-" + tier}
              style={{ left: ringCx + PAD_X - r, top: ringCy + PAD_Y - r, width: 2 * r, height: 2 * r }}>
              <span className="tier-label">{TIER_ROMAN[tier - 1]}</span>
            </div>
          ))}
          {data.islands.map(isl => {
            const wonder = wonderByIsland.get(isl.id);
            if (wonder){
              const size = 124;
              return (
                <div className={"island wonder-island tone-" + wonder.status.toLowerCase()} key={isl.id}
                  style={{ left: isl.px + PAD_X, top: isl.py + PAD_Y, width: size, height: size }}
                  onClick={() => { if (!wasDragged()) setWonderSel(wonder); }}
                  title={`${wonder.name} — ${wonder.status}`}>
                  <div className="island-land wonder-land" style={{ borderRadius: blobRadius(isl.id) }}>
                    <span className="wonder-ico">{WONDER_ICON[wonder.kind]}</span>
                    {wonder.level > 0 && <span className="wonder-lvl-badge">Lv {wonder.level}</span>}
                  </div>
                  <div className="nm">{isl.name}</div>
                </div>
              );
            }
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
            const size = ISLAND_SIZE;   // constant — every island is the same size (start island included)
            const mine = isl.cities.some(c => c.faction === "self");
            const free = SLOTS_PER_ISLAND - n;
            // green = players may spawn here (Tier-1 surrounding islands); red = founding/conquest only
            const spawnCls = isl.spawnable ? " spawn-green" : " spawn-red";
            const spawnTip = isl.spawnable ? "spawnable" : "no spawn — found/conquer only";
            return (
              <div className="island" key={isl.id} style={{ left: isl.px + PAD_X, top: isl.py + PAD_Y, width: size, height: size }}
                onClick={() => { if (!wasDragged()) openIsland(isl); }} title={`${isl.name} · T${isl.tier ?? "?"} · ${spawnTip} — ${free} free plot${free === 1 ? "" : "s"}`}>
                <div className={"island-land" + spawnCls} style={{ borderRadius: blobRadius(isl.id) }}>
                  {mine && <span className="island-star">★</span>}
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
          {/* roaming Colossi — live markers on the Tier 2/3 ring with a shared health bar */}
          {colossi.map(c => {
            const hpPct = Math.max(0, Math.round((c.currentHealth / Math.max(1, c.maxHealth)) * 100));
            return (
              <div className="colossus-marker" key={c.id}
                style={{ left: c.x + PAD_X, top: c.y + PAD_Y }}
                title={`${c.name} — ${hpPct}% HP`}
                onClick={(e) => { e.stopPropagation(); if (!wasDragged()) setColossusSel(c.id); }}>
                <span className="colossus-ico">🐙</span>
                <div className="colossus-hp"><i style={{ width: hpPct + "%" }} /></div>
                <div className="colossus-nm">{c.name}</div>
              </div>
            );
          })}
          {/* live troop-movement lines (own movements + incoming attacks), markers glide in real time */}
          <MovementLines movements={movements} islands={data.islands} prefs={linePrefs}
            space={WSPACE} padX={PAD_X} padY={PAD_Y}
            onOpenCity={(id) => { const isl = data.islands.find(i => i.cities.some(c => c.id === id)); if (isl) openIsland(isl); }} />
        </div>
        </div>
      </div>

      {/* colossus → live detail + attack */}
      {colossusSel != null && (
        <ColossusPanel colossusId={colossusSel} myUnits={myUnits} activeCityId={activeCityId}
          onClose={() => setColossusSel(null)} onChanged={() => { load(); onChanged(); }} setErr={setErr} />
      )}


      {/* wonder island → capture / invest */}
      {wonderSel && (
        <WonderModal wonder={wonderSel}
          ctx={{ myPlayerId, activeCityId, myUnits, heroes: heroesHere, setErr, onChanged: () => { load(); onChanged(); } }}
          onClose={() => setWonderSel(null)} onChanged={() => { load(); onChanged(); }} />
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
                          <td>{c.faction !== "self" && c.faction !== "ally" && <>
                            <button className="btn" onClick={() => openRaid(c)}>⚔ Attack</button>
                            <button className="btn ghost" title="Send a spy from your selected city"
                              onClick={async () => { setSpyNote("");
                                try { const r = await launchSpy(activeCityId, c.id); setSpyNote(`🕵 Spy en route to ${c.name} — resolves ${r.resolvesAt ? "in ~" + Math.max(1, Math.round((new Date(r.resolvesAt).getTime() - Date.now())/60000)) + "m" : "soon"}. Check Spy Reports.`); onChanged(); }
                                catch (e: any) { setSpyNote(e.message); } }}>🕵 Spy</button>
                          </>}
                          {(c.faction === "ally" || (c.faction === "self" && c.id !== activeCityId)) && (
                            <button className="btn ghost" title="Send troops to defend this city — they stay to protect it"
                              onClick={() => openSupport(c)}>🤝 Support</button>
                          )}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
                {spyNote && <p className="muted" style={{ marginTop: 6 }}>{spyNote}</p>}
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
                    <div className="raid-allrow">
                      <span className="muted">Select troops to send from your active city:</span>
                      <span className="raid-allbtns">
                        <button type="button" className="btn ghost tiny" onClick={() => setRaidCounts(Object.fromEntries(myUnits.map(u => [u.type, u.count])))}>Select all</button>
                        <button type="button" className="btn ghost tiny" onClick={() => setRaidCounts({})}>Clear</button>
                      </span>
                    </div>
                    {myUnits.map(u => (
                      <div key={u.type} className="raid-row">
                        <span>{titleCase(u.type)} <small className="muted">({u.count} available)</small></span>
                        <input type="number" min={0} max={u.count} value={raidCounts[u.type] || 0}
                          onChange={e => setRaidCounts({ ...raidCounts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
                      </div>
                    ))}
                    <HeroPicker heroes={heroesHere} value={raidHeroId} onChange={(id) => { setRaidHeroId(id); if (id == null) setRaidSiege(false); }} />
                    {raidHeroId != null && (
                      <label className="siege-toggle">
                        <input type="checkbox" checked={raidSiege} onChange={e => setRaidSiege(e.target.checked)} />
                        <span>⚑ Lay siege <small className="muted">— hero-led; needs ≥1 Defense ship. Wins lock your force onto the city until it falls or is broken.</small></span>
                      </label>
                    )}
                    <TravelPreview originCityId={activeCityId} targetCityId={raidTarget.id} units={raidCounts} heroId={raidHeroId} attack />
                    <button className="btn" onClick={sendRaid}>{raidSiege ? "⚑ March to lay siege" : "⚔ Send attack"}</button>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* support troop picker — reinforce a friendly city */}
      {supportTarget && (
        <div className="modal-backdrop" onClick={() => setSupportTarget(null)}>
          <div className="modal-window" onClick={e => e.stopPropagation()} style={{ width: "min(460px,100%)" }}>
            <div className="modal-header">
              <h2>🤝 Support {supportTarget.name}</h2>
              <button className="modal-close" onClick={() => setSupportTarget(null)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="popup-panel">
                {myUnits.length === 0 ? (
                  <p className="muted">No troops in your active city. Train some in the Barracks or Harbor first.</p>
                ) : (
                  <>
                    <p className="muted">Send troops from your active city to defend <b>{supportTarget.name}</b>. They march there and
                      stay stationed, fighting for it when it is attacked.</p>
                    {myUnits.map(u => (
                      <div key={u.type} className="raid-row">
                        <span>{titleCase(u.type)} <small className="muted">({u.count} available)</small></span>
                        <input type="number" min={0} max={u.count} value={supportCounts[u.type] || 0}
                          onChange={e => setSupportCounts({ ...supportCounts, [u.type]: Math.max(0, Math.min(u.count, +e.target.value)) })} />
                      </div>
                    ))}
                    <TravelPreview originCityId={activeCityId} targetCityId={supportTarget.id} units={supportCounts} />
                    <button className="btn" onClick={sendSupport}>🤝 Send support</button>
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
