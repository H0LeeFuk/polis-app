import { useEffect, useRef, useState } from "react";
import { getWorld, doColonize, doRaid } from "../api";
import type { WorldData, WorldIsland } from "../types";

const fmt = (n: number) => n >= 10000 ? (n / 1000).toFixed(1) + "k" : Math.floor(n).toString();

export default function WorldView({ activeCityId, onChanged, setErr }: {
  activeCityId: number; onChanged: () => void; setErr: (s: string) => void;
}) {
  const [data, setData] = useState<WorldData | null>(null);
  const [sel, setSel] = useState<WorldIsland | null>(null);
  const scroller = useRef<HTMLDivElement>(null);

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
  const raid = (targetCityId: number) => {
    const n = Number(prompt("Send how many Hoplites on this raid?", "10"));
    if (n > 0) act(() => doRaid(activeCityId, targetCityId, { HOPLITE: n }));
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", position: "relative" }}>
      <button className="centerbtn" onClick={centerOnMyCity}>⌖ Center on my city</button>
      <div className="world" ref={scroller} style={{ flex: 1, minHeight: 0 }}>
        <div className="wspace">
          {data.islands.map(isl => (
            <div className="island" key={isl.id} style={{ left: isl.px, top: isl.py }}
              onClick={() => setSel(isl)}>
              <div className="disc" title={isl.name}>{isl.cities.length}</div>
              <div className="nm">{isl.name}</div>
            </div>
          ))}
        </div>
      </div>

      {sel && (
        <div className="panel" style={{ marginTop: 0, maxHeight: "35%", overflowY: "auto", borderTop: "1px solid #3a2a1d" }}>
          <h2>{sel.name}</h2>
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
                    <td className={"faction-" + c.faction}>{c.name}</td>
                    <td className="muted">{c.faction}</td>
                    <td>{fmt(c.points)}</td>
                    <td>{c.faction !== "self" && c.faction !== "ally" &&
                      <button className="btn" onClick={() => raid(c.id)}>Raid</button>}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
