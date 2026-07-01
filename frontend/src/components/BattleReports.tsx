import { useCallback, useEffect, useRef, useState } from "react";
import {
  getMyReports, getCityReports, getReport, deleteReport, markAllReportsRead,
  getSpyReports, getSpyAlerts,
} from "../api";
import { useDraggable } from "../useDraggable";
import type {
  BattleReport, BattleReportSummary, BattleOutcome, CitySummary,
  SpyReportDto, SpyAlertDto,
} from "../types";
import { UNIT_GLYPH } from "../movements";

const RES_GLYPH: Record<string, string> = {
  WOOD: "🪵", STONE: "🪨", WHEAT: "🌾",
  COAL: "⬛", CRYSTALS: "💎", IRON: "⛓", PEARLS: "🫧",
};
const RES_ORDER = ["WOOD", "STONE", "WHEAT", "COAL", "CRYSTALS", "IRON", "PEARLS"];
const ELEMENT_GLYPH: Record<string, string> = { FIRE: "🔥", WIND: "🌬", EARTH: "🌍", WATER: "💧" };
const ELEMENT_ORDER = ["FIRE", "WIND", "EARTH", "WATER"];
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();
const glyph = (t: string) => UNIT_GLYPH[t] ?? "⚔";
const sumVals = (m: Record<string, number> | null | undefined) =>
  m ? Object.values(m).reduce((a, b) => a + b, 0) : 0;

/** "Today 16:32" / "Yesterday 09:14" / "16 Jun 14:05". */
function fmtWhen(iso: string): string {
  const d = new Date(iso);
  const hm = d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  const now = new Date();
  const dayDiff = Math.floor((startOfDay(now) - startOfDay(d)) / 86400000);
  if (dayDiff === 0) return `Today ${hm}`;
  if (dayDiff === 1) return `Yesterday ${hm}`;
  return `${d.toLocaleDateString([], { day: "numeric", month: "short" })} ${hm}`;
}
function fmtWhenFull(iso: string): string {
  const d = new Date(iso);
  return `${d.toLocaleDateString([], { day: "numeric", month: "long", year: "numeric" })} — ` +
    d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}
const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();

// outcome interpreted from the viewer's side: tone + label/icon
function viewerLens(r: { outcome: BattleOutcome; role: string }) {
  const attacker = r.role === "ATTACKER";
  if (r.outcome === "DRAW") return { tone: "draw", icon: "⚖", label: "DRAW" };
  if (attacker)
    return r.outcome === "VICTORY"
      ? { tone: "good", icon: "⚔", label: "VICTORY" }
      : { tone: "bad", icon: "💀", label: "DEFEAT" };
  // defender perspective
  return r.outcome === "VICTORY"      // attacker won → city was plundered
    ? { tone: "bad", icon: "🏴", label: "PLUNDERED" }
    : { tone: "good", icon: "🛡", label: "DEFENDED" };
}

// ============================================================================
// Report card (used in both the global list and the city tab)
// ============================================================================

export function ReportCard({ r, active, onOpen }: {
  r: BattleReportSummary; active: boolean; onOpen: () => void;
}) {
  const lens = viewerLens(r);
  const attacker = r.role === "ATTACKER";
  const plundered = r.outcome === "VICTORY";
  const stolenTotal = sumVals(r.resourcesStolen);
  const foe = attacker ? (r.defenderPlayerName ?? "Barbarians") : r.attackerPlayerName;

  return (
    <div className={`br-card tone-${lens.tone}` + (r.unread ? " unread" : "") + (active ? " active" : "")}
      onClick={onOpen}>
      <div className="br-card-top">
        <span className="br-verdict">{lens.icon} {lens.label}</span>
        {r.siegeStarted && <span className="br-siege-badge" title={attacker ? "This attack laid a siege" : "This attack laid a siege on your city"}>🏰 Siege begun</span>}
        <span className="br-route"><b>{r.attackerCityName}</b> → <b>{r.defenderCityName}</b></span>
      </div>
      <div className="br-card-sub">
        <span className="muted">{attacker ? "vs" : "by"} {foe}</span>
        <span className="br-when">🕐 {fmtWhen(r.foughtAt)}{r.unread && <span className="br-new">NEW</span>}</span>
      </div>
      <div className="br-card-stats">
        {attacker ? (
          <>
            <span>⚔ Sent: {r.attackerSent}</span>
            <span className="loss">Lost: {r.attackerLost}</span>
            <span className="kill">💀 Killed: {r.defenderLost}</span>
          </>
        ) : (
          <>
            <span className="kill">💀 Their losses: {r.attackerLost}</span>
            <span className="loss">Your losses: {r.defenderLost}</span>
          </>
        )}
      </div>
      <div className="br-card-foot">
        {plundered && stolenTotal > 0 ? (
          <span className="br-plunder">
            {RES_ORDER.filter(k => (r.resourcesStolen[k] ?? 0) > 0).map(k =>
              <span key={k}>{RES_GLYPH[k]} {attacker ? "+" : "−"}{r.resourcesStolen[k]}</span>)}
          </span>
        ) : <span className="muted">Resources stolen: none</span>}
        <button className="br-view">View Report →</button>
      </div>
    </div>
  );
}

// ============================================================================
// Report detail (full breakdown) — reused by global panel and city tab
// ============================================================================

function TroopTable({ title, present, lost, survived, presentLabel }: {
  title: string; present: Record<string, number>; lost: Record<string, number>;
  survived: Record<string, number>; presentLabel: string;
}) {
  // iterate the unit keys actually present in the data (not a hardcoded catalog) so any unit —
  // race ships like FIRE_RAM/GALLEY, summons, future units — always shows. glyph() falls back to ⚔.
  const types = Array.from(new Set([
    ...Object.keys(present ?? {}), ...Object.keys(lost ?? {}), ...Object.keys(survived ?? {}),
  ])).filter(t => (present[t] ?? 0) > 0 || (lost[t] ?? 0) > 0 || (survived[t] ?? 0) > 0);
  return (
    <div className="br-army">
      <h4>{title}</h4>
      <div className="br-trow br-thead"><span></span><span>{presentLabel}</span><span>Lost</span><span>Left</span></div>
      {types.length === 0
        ? <div className="br-trow empty muted"><span></span><span>—</span><span>—</span><span>—</span></div>
        : types.map(t => (
          <div className="br-trow" key={t}>
            <span title={titleCase(t)}>{glyph(t)} {titleCase(t)}</span>
            <span>{present[t] ?? 0}</span>
            <span className="loss">{lost[t] ?? 0}</span>
            <span className="surv">{survived[t] ?? 0}</span>
          </div>
        ))}
    </div>
  );
}

export function ReportDetail({ report, onClose, onDeleted, onAttackAgain }: {
  report: BattleReport;
  onClose: () => void;
  onDeleted: (id: number) => void;
  onAttackAgain?: (targetCityId: number, targetCityName: string) => void;
}) {
  const lens = viewerLens(report);
  const attacker = report.role === "ATTACKER";
  const stolenTotal = sumVals(report.resourcesStolen);
  const defFoe = report.defenderPlayerName ?? "Barbarians";
  const del = async () => {
    try { await deleteReport(report.id); onDeleted(report.id); } catch { /* surfaced by caller refresh */ }
  };

  return (
    <div className="br-detail">
      <div className="br-detail-head">
        <h2>⚔ Battle Report ⚔</h2>
        <span className="muted">{fmtWhenFull(report.foughtAt)}</span>
        {onClose && <button className="br-detail-close" onClick={onClose}>✕</button>}
      </div>

      <div className="br-sides">
        <div className={"br-side" + (attacker ? " you" : "")}>
          <span className="br-side-role">Attacker</span>
          <b>{report.attackerCityName}</b>
          <span className="muted">{report.attackerPlayerName}{attacker ? " (you)" : ""}</span>
        </div>
        <span className="br-vs">vs</span>
        <div className={"br-side" + (!attacker ? " you" : "")}>
          <span className="br-side-role">Defender</span>
          <b>{report.defenderCityName}</b>
          <span className="muted">{defFoe}{!attacker ? " (you)" : ""}</span>
        </div>
      </div>

      <div className={`br-outcome tone-${lens.tone}`}>
        {lens.icon} {lens.label}
        <small>{report.outcome === "VICTORY"
          ? (attacker ? "— Resources plundered" : "— Your city was plundered")
          : report.outcome === "DEFEAT"
            ? (attacker ? "— Your army was routed" : "— You held your ground")
            : "— Both sides withdrew"}</small>
      </div>
      {report.siegeStarted && (
        <div className="br-siege-note">🏰 This assault laid a <b>siege</b> — {attacker
          ? "hold it to conquer the city."
          : "your city is under siege. Break it before it falls."}</div>
      )}

      <div className="br-section-label">Forces</div>
      <div className="br-armies">
        <TroopTable title={attacker ? "Your forces" : "Enemy forces"} presentLabel="Sent"
          present={report.attackerTroopsSent} lost={report.attackerTroopsLost} survived={report.attackerTroopsSurvived} />
        <TroopTable title={attacker ? "Defender forces" : "Your forces"} presentLabel="Present"
          present={report.defenderTroopsPresent} lost={report.defenderTroopsLost} survived={report.defenderTroopsSurvived} />
      </div>
      <div className="br-powers">
        <span>Attack Power: <b>{report.attackerTotalAttackPower.toLocaleString()}</b></span>
        <span>Defence Power: <b>{report.defenderTotalDefencePower.toLocaleString()}</b></span>
      </div>

      {report.combatPointsEarned > 0 && (
        <div className="br-combat-pts">
          ⚔ <b>+{report.combatPointsEarned}</b> Combat Points earned
          {report.combatPointsReason && <small className="muted"> — {report.combatPointsReason}</small>}
        </div>
      )}

      <div className="br-section-label">Plunder</div>
      {report.outcome === "VICTORY" && stolenTotal > 0 ? (
        <div className="br-plunder-box">
          {RES_ORDER.filter(k => (report.resourcesStolen[k] ?? 0) > 0).map(k => (
            <div className="br-plunder-row" key={k}>
              <span>{RES_GLYPH[k]} {titleCase(k)}</span><b>{report.resourcesStolen[k] ?? 0}</b>
            </div>
          ))}
          <div className="br-plunder-total muted">Total: {stolenTotal.toLocaleString()} resources {attacker ? "looted" : "lost"}</div>
        </div>
      ) : <p className="muted">No resources stolen</p>}

      {report.heroName && (
        <>
          <div className="br-section-label">Hero</div>
          <div className="br-hero-box">
            <div className="br-hero-led">⚔ {report.heroName} (Level {report.heroLevel}) led the attack</div>
            <div className="muted">
              Bonuses: +{report.heroAttackBonusPct}% power · −{report.heroLossReductionPct}% losses
              {report.heroSkillUsed && <> · {report.heroSkillUsed.replace("_", " ")} used</>}
            </div>
            {report.heroXpGained > 0 && <div className="br-hero-xp">XP gained: +{report.heroXpGained}
              {report.heroLeveledTo && <b> 🎉 Reached level {report.heroLeveledTo}!</b>}</div>}
            <div className={report.heroWounded ? "br-hero-wounded" : "muted"}>
              Status: {report.heroWounded ? "WOUNDED — recovering" : "returned safely"}
            </div>
          </div>
        </>
      )}

      <div className="br-detail-actions">
        <button className="btn ghost danger" onClick={del}>🗑 Delete Report</button>
        {attacker && onAttackAgain && (
          <button className="btn" onClick={() => onAttackAgain(report.defenderCityId, report.defenderCityName)}>⚔ Attack Again</button>
        )}
      </div>
    </div>
  );
}

// ============================================================================
// Shared list logic (filters, paging, selection)
// ============================================================================

type Pill = "all" | "attacks" | "defenses" | "spies";
const ROLE_OF: Record<Pill, "ATTACKER" | "DEFENDER" | undefined> = {
  all: undefined, attacks: "ATTACKER", defenses: "DEFENDER", spies: undefined,
};

function useReports(load: (page: number) => Promise<{ content: BattleReportSummary[]; hasMore: boolean }>) {
  const [rows, setRows] = useState<BattleReportSummary[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const loadRef = useRef(load);
  loadRef.current = load;

  const reload = useCallback(async () => {
    setLoading(true);
    try { const r = await loadRef.current(0); setRows(r.content); setHasMore(r.hasMore); setPage(0); }
    catch { setRows([]); setHasMore(false); }
    finally { setLoading(false); }
  }, []);

  const more = useCallback(async () => {
    const next = page + 1;
    try { const r = await loadRef.current(next); setRows(p => [...p, ...r.content]); setHasMore(r.hasMore); setPage(next); }
    catch { /* keep current */ }
  }, [page]);

  return { rows, setRows, hasMore, loading, reload, more };
}

// ============================================================================
// AREA 1 — Global Battle Reports panel (full-screen)
// ============================================================================

export default function BattleReports({ cities, unreadCount = 0, onClose, onUnreadChange, onAttackAgain }: {
  cities: CitySummary[];
  unreadCount?: number;
  onClose: () => void;
  onUnreadChange?: () => void;
  onAttackAgain?: (targetCityId: number, targetCityName: string) => void;
}) {
  const win = useDraggable<HTMLDivElement>();
  const [pill, setPill] = useState<Pill>("all");
  const [cityId, setCityId] = useState<number | "">("");
  const [selected, setSelected] = useState<BattleReport | null>(null);

  const load = useCallback((page: number) => getMyReports({
    page, size: 20,
    role: ROLE_OF[pill],
    cityId: cityId === "" ? undefined : cityId,
  }), [pill, cityId]);

  const { rows, setRows, hasMore, loading, reload, more } = useReports(load);
  useEffect(() => { reload(); }, [load]);   // refetch when the filter (pill/city) changes

  const open = async (id: number) => {
    try {
      const full = await getReport(id);
      setSelected(full);
      setRows(rs => rs.map(r => r.id === id ? { ...r, unread: false } : r));
      onUnreadChange?.();
    } catch { /* ignore */ }
  };
  const afterDelete = (id: number) => {
    setRows(rs => rs.filter(r => r.id !== id));
    setSelected(null);
    onUnreadChange?.();
  };
  const markAll = async () => {
    try { await markAllReportsRead(); setRows(rs => rs.map(r => ({ ...r, unread: false }))); onUnreadChange?.(); }
    catch { /* ignore */ }
  };

  const pills: { id: Pill; label: string }[] = [
    { id: "all", label: "All" }, { id: "attacks", label: "Attacks" },
    { id: "defenses", label: "Defenses" }, { id: "spies", label: "Spies" },
  ];
  const isSpies = pill === "spies";
  const showMarkAll = !isSpies && (unreadCount > 0 || rows.some(r => r.unread));

  return (
    <div className="mvov-backdrop" onClick={onClose}>
      <div className="mvov br-panel" ref={win} onClick={e => e.stopPropagation()}>
        <div className="mvov-head">
          <h2>📜 Battle Reports</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        <div className="mvov-filters">
          {pills.map(p => (
            <button key={p.id} className={"mvov-fbtn" + (pill === p.id ? " active" : "")} onClick={() => { setPill(p.id); setSelected(null); }}>{p.label}</button>
          ))}
          {!isSpies && (
            <select className="mvov-cityfilter" value={cityId} onChange={e => { setCityId(e.target.value === "" ? "" : Number(e.target.value)); setSelected(null); }}>
              <option value="">All cities</option>
              {cities.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          )}
          {showMarkAll && <button className="mvov-fbtn br-markall" onClick={markAll}>✓ Mark all read</button>}
        </div>

        {isSpies ? <SpyReportsPane /> : (
          <div className="br-body">
            <div className="br-list">
              {loading ? <p className="muted br-loading">Unrolling the scrolls…</p>
                : rows.length === 0 ? <ReportsEmpty />
                  : <>
                    {rows.map(r => <ReportCard key={r.id} r={r} active={selected?.id === r.id} onOpen={() => open(r.id)} />)}
                    {hasMore && <button className="btn ghost br-more" onClick={more}>Load more</button>}
                  </>}
            </div>
            <div className={"br-detail-pane" + (selected ? " open" : "")}>
              {selected
                ? <ReportDetail report={selected} onClose={() => setSelected(null)} onDeleted={afterDelete} onAttackAgain={onAttackAgain} />
                : <div className="br-detail-placeholder muted">Select a report to read the full account.</div>}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ============================================================================
// Spies tab — espionage intel + caught-spy alerts (sourced from the spy system)
// ============================================================================

function spyAgo(iso: string): string {
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (s < 60) return s + "s ago";
  const m = Math.floor(s / 60); if (m < 60) return m + "m ago";
  const h = Math.floor(m / 60); return h + "h ago";
}
const spyFmt = (n: number) => n >= 1000 ? (n / 1000).toFixed(1) + "k" : Math.floor(n).toString();

function SpyReportsPane() {
  const [reports, setReports] = useState<SpyReportDto[]>([]);
  const [alerts, setAlerts] = useState<SpyAlertDto[]>([]);
  const [open, setOpen] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    Promise.all([getSpyReports().catch(() => []), getSpyAlerts().catch(() => [])])
      .then(([r, a]) => { if (alive) { setReports(r); setAlerts(a); } })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, []);

  if (loading) return <div className="spy-pane"><p className="muted br-loading">Decoding intelligence…</p></div>;

  return (
    <div className="spy-pane">
        {alerts.length > 0 && (
          <>
            <div className="br-section-label">⚠ Spies caught (you were scouted)</div>
            {alerts.map(a => (
              <div className="spy-alert" key={"a" + a.id}>
                Enemy spy from <b>{a.spyingPlayerName}</b> caught scouting <b>{a.targetCityName}</b> · <span className="muted">{spyAgo(a.caughtAt)}</span>
              </div>
            ))}
          </>
        )}
        <div className="br-section-label">Spy reports (intel gathered)</div>
        {reports.length === 0 && <p className="muted">No spy reports yet. Send a spy from the world map.</p>}
        {reports.map(r => (
          <div className={"spy-report " + (r.outcome === "SUCCESS" ? "ok" : "caught")} key={r.id}>
            <div className="spy-report-head" onClick={() => setOpen(open === r.id ? null : r.id)}>
              <span>{r.outcome === "SUCCESS" ? "✅" : "🚨"} {r.targetCityName}</span>
              <span className="muted">{r.outcome === "SUCCESS" ? "Intel" : "Caught"} · {spyAgo(r.capturedAt)}</span>
            </div>
            {open === r.id && r.outcome === "SUCCESS" && (
              <div className="spy-report-body">
                <p className="muted" style={{ fontSize: 11 }}>Intel from {spyAgo(r.capturedAt)} — defenses may have changed.</p>
                <div className="spy-grid">
                  <div>
                    <h4>Troops</h4>
                    {r.troops && Object.keys(r.troops).length
                      ? Object.entries(r.troops).map(([t, n]) => <div key={t}>{titleCase(t)} <b>×{n}</b></div>)
                      : <span className="muted">None garrisoned</span>}
                  </div>
                  <div>
                    <h4>Resources</h4>
                    {r.resources && Object.entries(r.resources).map(([t, n]) => <div key={t}>{titleCase(t)} <b>{spyFmt(n)}</b></div>)}
                  </div>
                  <div>
                    <h4>Buildings</h4>
                    {r.buildings && Object.entries(r.buildings).filter(([, l]) => l > 0).length
                      ? Object.entries(r.buildings).filter(([, l]) => l > 0).map(([t, l]) => <div key={t}>{titleCase(t)} <b>Lv {l}</b></div>)
                      : <span className="muted">Undeveloped</span>}
                  </div>
                </div>
              </div>
            )}
            {open === r.id && r.outcome === "CAUGHT" && (
              <div className="spy-report-body"><p className="muted">Your spy was caught — no intel, and the target now knows it was you.</p></div>
            )}
          </div>
        ))}
    </div>
  );
}

// ============================================================================
// AREA 2 — City-scoped report list (rendered inside the city Movements panel)
// ============================================================================

export function CityReportsList({ cityId, onAttackAgain }: {
  cityId: number; onAttackAgain?: (targetCityId: number, targetCityName: string) => void;
}) {
  const [selected, setSelected] = useState<BattleReport | null>(null);
  const load = useCallback((page: number) => getCityReports(cityId, { page, size: 20 }), [cityId]);
  const { rows, setRows, hasMore, loading, reload, more } = useReports(load);
  useEffect(() => { reload(); }, [load]);   // refetch when the filter (pill/city) changes

  const open = async (id: number) => {
    try { setSelected(await getReport(id)); setRows(rs => rs.map(r => r.id === id ? { ...r, unread: false } : r)); }
    catch { /* ignore */ }
  };

  return (
    <div className="cm-reports">
      {loading ? <p className="muted">Loading…</p>
        : rows.length === 0 ? <ReportsEmpty small />
          : <>
            {rows.map(r => <ReportCard key={r.id} r={r} active={false} onOpen={() => open(r.id)} />)}
            {hasMore && <button className="btn ghost br-more" onClick={more}>Load more</button>}
          </>}
      {selected && (
        <div className="br-sheet-backdrop" onClick={() => setSelected(null)}>
          <div className="br-sheet" onClick={e => e.stopPropagation()}>
            <ReportDetail report={selected} onClose={() => setSelected(null)}
              onDeleted={(id) => { setRows(rs => rs.filter(r => r.id !== id)); setSelected(null); }}
              onAttackAgain={onAttackAgain} />
          </div>
        </div>
      )}
    </div>
  );
}

function ReportsEmpty({ small }: { small?: boolean }) {
  return (
    <div className={"br-empty" + (small ? " small" : "")}>
      <svg viewBox="0 0 64 48" width={small ? 48 : 72} height={small ? 36 : 54} aria-hidden>
        <rect x="10" y="6" width="44" height="36" rx="3" fill="#e8d8b0" stroke="#7a5a22" strokeWidth="2" />
        <path d="M10 9 q-6 0 -6 6 q0 6 6 6 Z" fill="#d8c79c" stroke="#7a5a22" strokeWidth="2" />
        <path d="M54 9 q6 0 6 6 q0 6 -6 6 Z" fill="#d8c79c" stroke="#7a5a22" strokeWidth="2" />
        <path d="M20 18 L44 18 M20 26 L44 26 M20 34 L36 34" stroke="#b08642" strokeWidth="2" strokeLinecap="round" />
      </svg>
      <p className="muted">No battles recorded yet — glory awaits.</p>
    </div>
  );
}
