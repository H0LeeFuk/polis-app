import { useEffect, useRef, useState } from "react";
import { getTradeMarket, tradeBuyPreview, tradeBuy, tradeSell, cancelTradeListing } from "../api";
import type { TradeMarket, BuyPreview } from "../types";

const RES = ["WOOD", "STONE", "WHEAT", "COAL", "CRYSTALS", "IRON", "PEARLS"] as const;
const RES_GLYPH: Record<string, string> = {
  WOOD: "🪵", STONE: "🪨", WHEAT: "🌾",
  COAL: "⬛", CRYSTALS: "💎", IRON: "⛓", PEARLS: "🫧",
};
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const fmt = (n: number) => n >= 10000 ? (n / 1000).toFixed(n >= 100000 ? 0 : 1) + "k" : Math.floor(n).toString();

/** Human delivery time: "2h 05m", "12m 30s", "8s". */
function fmtDur(s: number): string {
  if (s <= 0) return "instant";
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  if (h > 0) return `${h}h ${m.toString().padStart(2, "0")}m`;
  if (m > 0) return `${m}m ${sec.toString().padStart(2, "0")}s`;
  return `${sec}s`;
}
function etaFrom(arriveAt: string | null, now: number): string {
  if (!arriveAt) return "queued";
  return fmtDur(Math.max(0, Math.round((new Date(arriveAt).getTime() - now) / 1000)));
}

type Tab = "buy" | "sell" | "listings" | "convoys";

export default function TradePanel({ cityId, onClose, onChanged }: {
  cityId: number; onClose: () => void; onChanged?: () => void;
}) {
  const [data, setData] = useState<TradeMarket | null>(null);
  const [tab, setTab] = useState<Tab>("buy");
  const [err, setErr] = useState("");
  const [now, setNow] = useState(Date.now());

  const refresh = () => getTradeMarket(cityId).then(setData).catch(e => setErr(e.message));
  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, [cityId]);
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t); }, []);

  if (!data) {
    return (
      <div className="modal-backdrop" onClick={onClose}>
        <div className="modal-window" onClick={e => e.stopPropagation()}>
          <div className="modal-header"><h2>Marketplace</h2><button className="modal-close" onClick={onClose}>✕</button></div>
          <div className="modal-body"><p className="muted">{err || "Loading the market…"}</p></div>
        </div>
      </div>
    );
  }

  const acted = () => { refresh(); onChanged?.(); };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-window trade-window" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>🔁 Marketplace — {data.cityName}</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        <div className="trade-cap">
          <span><b>Market Lv {data.marketLevel}</b></span>
          <span title="Units one convoy can carry">📦 {fmt(data.convoyCapacity)} / convoy</span>
          <span title="How many convoys can travel at once to this city">🚚 {data.maxSimultaneousConvoys} at once</span>
          <span title="Convoy pace">🐢 {data.convoySpeedMinutesPerTile} min/tile</span>
          <span className="trade-gold">⭐ {fmt(data.gold)} gold</span>
          <small className="muted">Upgrade the Agora to carry more & ship faster.</small>
        </div>

        <div className="trade-tabs">
          {(["buy", "sell", "listings", "convoys"] as Tab[]).map(t => (
            <button key={t} className={"trade-tab" + (tab === t ? " active" : "")} onClick={() => setTab(t)}>
              {t === "buy" ? "Buy" : t === "sell" ? "Sell" : t === "listings" ? `My Listings (${data.myListings.length})` : `Convoys (${data.convoys.length})`}
            </button>
          ))}
        </div>

        {err && <div className="trade-err" onClick={() => setErr("")}>{err}</div>}

        <div className="modal-body">
          {tab === "buy" && <BuyTab data={data} cityId={cityId} setErr={setErr} onDone={acted} />}
          {tab === "sell" && <SellTab data={data} cityId={cityId} setErr={setErr} onDone={acted} />}
          {tab === "listings" && <ListingsTab data={data} cityId={cityId} setErr={setErr} onDone={acted} />}
          {tab === "convoys" && <ConvoysTab data={data} now={now} />}
        </div>
      </div>
    </div>
  );
}

function BuyTab({ data, cityId, setErr, onDone }: {
  data: TradeMarket; cityId: number; setErr: (s: string) => void; onDone: () => void;
}) {
  const [res, setRes] = useState<string>("WOOD");
  const [bundles, setBundles] = useState(5);
  const [maxPrice, setMaxPrice] = useState(100);
  const [deliveryCity, setDeliveryCity] = useState<number>(data.deliveryCities.find(c => c.id === cityId)?.id ?? data.deliveryCities[0]?.id ?? cityId);
  const [preview, setPreview] = useState<BuyPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const book = data.book[res] ?? [];

  // debounced preview whenever the order parameters change
  useEffect(() => {
    if (bundles <= 0 || maxPrice <= 0) { setPreview(null); return; }
    const h = setTimeout(() => {
      tradeBuyPreview(cityId, res, bundles, maxPrice, deliveryCity)
        .then(setPreview).catch(() => setPreview(null));
    }, 300);
    return () => clearTimeout(h);
  }, [cityId, res, bundles, maxPrice, deliveryCity]);

  const buy = async () => {
    setErr(""); setBusy(true);
    try { await tradeBuy(cityId, { resourceType: res, bundles, maxPricePerBundle: maxPrice, deliveryCityId: deliveryCity }); onDone(); }
    catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };

  return (
    <div className="trade-grid">
      <div className="trade-col">
        <div className="res-chips">
          {RES.map(r => (
            <button key={r} className={"res-chip" + (res === r ? " sel" : "")} onClick={() => setRes(r)}>
              {RES_GLYPH[r]} {titleCase(r)}
            </button>
          ))}
        </div>
        <h4 className="trade-h">Order book — cheapest first</h4>
        {book.length === 0
          ? <p className="muted">No {titleCase(res)} listed yet. Be the first to sell.</p>
          : <div className="book">
            <div className="book-row book-head"><span>Price/bundle</span><span>Bundles</span><span>Seller</span><span>From</span></div>
            {book.map(row => (
              <div className={"book-row" + (row.mine ? " mine" : "")} key={row.listingId}>
                <span className="book-price">⭐ {row.pricePerBundle}</span>
                <span>{row.bundles}</span>
                <span className="book-seller">{row.seller}{row.mine ? " (you)" : ""}</span>
                <span className="muted">{row.sourceCity} · {row.sourceIsland}</span>
              </div>
            ))}
          </div>}
      </div>

      <div className="trade-col">
        <h4 className="trade-h">Place an order</h4>
        <label className="trade-field"><span>Bundles ({data.bundleSize} units each)</span>
          <input type="number" min={1} value={bundles} onChange={e => setBundles(Math.max(1, +e.target.value))} /></label>
        <label className="trade-field"><span>Max price per bundle (gold)</span>
          <input type="number" min={1} value={maxPrice} onChange={e => setMaxPrice(Math.max(1, +e.target.value))} /></label>
        <label className="trade-field"><span>Deliver to</span>
          <select value={deliveryCity} onChange={e => setDeliveryCity(+e.target.value)}>
            {data.deliveryCities.map(c => <option key={c.id} value={c.id}>{c.name} · {c.island}</option>)}
          </select></label>

        <div className="delivery-panel">
          {!preview || preview.filledBundles === 0 ? (
            <p className="muted">No matching listings at or below your price.</p>
          ) : (
            <>
              <div className="dp-row"><span>Fills</span><b>{preview.filledBundles} / {preview.requestedBundles} bundles</b></div>
              <div className="dp-row"><span>Total cost</span><b className={preview.affordable ? "" : "lack"}>⭐ {fmt(preview.totalGold)} gold</b></div>
              <div className="dp-row"><span>Delivered to {data.deliveryCities.find(c => c.id === deliveryCity)?.name}</span>
                <b>{fmtDur(preview.totalDeliveryTime)}</b></div>
              <div className="dp-row"><span>Convoys</span>
                <b>{preview.convoyCount} (Lv {preview.marketLevel} carries {fmt(preview.convoyCapacity)} each)</b></div>
              {preview.perConvoy.length > 0 && (
                <div className="dp-per">
                  {preview.perConvoy.map((p, i) => (
                    <div className="dp-per-row" key={i}>
                      <span>{p.sourceCity}: {fmt(p.units)} units</span>
                      <span className="muted">{p.convoys} convoy{p.convoys > 1 ? "s" : ""} · {fmtDur(p.etaSeconds)}</span>
                    </div>
                  ))}
                </div>
              )}
              {preview.splitReason && <div className="dp-note">ℹ {preview.splitReason}</div>}
              <small className="muted">Payment is immediate; goods arrive over time.</small>
            </>
          )}
        </div>
        <button className="btn" disabled={busy || !preview || preview.filledBundles === 0 || !preview.affordable} onClick={buy}>
          {preview && !preview.affordable ? "Not enough gold" : "🔁 Buy & ship"}
        </button>
      </div>
    </div>
  );
}

function SellTab({ data, cityId, setErr, onDone }: {
  data: TradeMarket; cityId: number; setErr: (s: string) => void; onDone: () => void;
}) {
  const [res, setRes] = useState<string>("WOOD");
  const [bundles, setBundles] = useState(5);
  const [price, setPrice] = useState(100);
  const [busy, setBusy] = useState(false);
  const units = bundles * data.bundleSize;

  const sell = async () => {
    setErr(""); setBusy(true);
    try { await tradeSell(cityId, { resourceType: res, bundles, pricePerBundle: price }); onDone(); }
    catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };
  return (
    <div className="trade-col solo">
      <p className="muted">List resources from <b>{data.cityName}</b> for gold. They're escrowed out of the city
        now; a buyer's purchase pays you instantly and ships the goods to them.</p>
      <div className="res-chips">
        {RES.map(r => (
          <button key={r} className={"res-chip" + (res === r ? " sel" : "")} onClick={() => setRes(r)}>
            {RES_GLYPH[r]} {titleCase(r)}
          </button>
        ))}
      </div>
      <label className="trade-field"><span>Bundles ({data.bundleSize} units each)</span>
        <input type="number" min={1} value={bundles} onChange={e => setBundles(Math.max(1, +e.target.value))} /></label>
      <label className="trade-field"><span>Price per bundle (gold)</span>
        <input type="number" min={1} value={price} onChange={e => setPrice(Math.max(1, +e.target.value))} /></label>
      <div className="delivery-panel">
        <div className="dp-row"><span>Escrowed now</span><b>{RES_GLYPH[res]} {fmt(units)} {titleCase(res)}</b></div>
        <div className="dp-row"><span>Earn if fully sold</span><b>⭐ {fmt(bundles * price)} gold</b></div>
      </div>
      <button className="btn" disabled={busy} onClick={sell}>📦 List for sale</button>
    </div>
  );
}

function ListingsTab({ data, cityId, setErr, onDone }: {
  data: TradeMarket; cityId: number; setErr: (s: string) => void; onDone: () => void;
}) {
  const cancel = async (id: number) => {
    setErr("");
    try { await cancelTradeListing(cityId, id); onDone(); } catch (e: any) { setErr(e.message); }
  };
  if (data.myListings.length === 0) return <p className="muted">You have no active listings.</p>;
  return (
    <div className="my-listings">
      {data.myListings.map(l => (
        <div className="ml-row" key={l.listingId}>
          <span className="ml-res">{RES_GLYPH[l.resourceType]} {titleCase(l.resourceType)}</span>
          <span>{l.bundles} × {data.bundleSize}</span>
          <span>⭐ {l.pricePerBundle}/bundle</span>
          <span className="muted">{l.sourceCity}</span>
          <button className="btn ghost danger" onClick={() => cancel(l.listingId)}>Cancel</button>
        </div>
      ))}
    </div>
  );
}

function ConvoysTab({ data, now }: { data: TradeMarket; now: number }) {
  if (data.convoys.length === 0) return <p className="muted">No trade convoys in motion.</p>;
  return (
    <div className="convoy-list">
      {data.convoys.map(cv => {
        const cargo = Object.entries(cv.cargo).filter(([, v]) => v > 0);
        const pct = cv.departAt && cv.arriveAt
          ? Math.max(0, Math.min(100, Math.round(((now - new Date(cv.departAt).getTime()) / (new Date(cv.arriveAt).getTime() - new Date(cv.departAt).getTime())) * 100)))
          : 0;
        return (
          <div className={"convoy-row" + (cv.status === "PENDING" ? " pending" : "")} key={cv.id}>
            <div className="cv-top">
              <span className="cv-route">🚚 {cv.origin} → <b>{cv.destination}</b></span>
              <span className="cv-eta">{cv.status === "PENDING" ? "queued" : etaFrom(cv.arriveAt, now)}</span>
            </div>
            <div className="cv-sub">
              <span className="cv-cargo">{cargo.map(([k, v]) => <span key={k}>{RES_GLYPH[k]} {fmt(v)}</span>)}</span>
              <span className="muted">{cv.status === "PENDING" ? "Waiting for a convoy slot" : "In transit"}</span>
            </div>
            <div className="cv-bar"><i style={{ width: pct + "%" }} /></div>
          </div>
        );
      })}
    </div>
  );
}
