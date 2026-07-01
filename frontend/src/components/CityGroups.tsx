import { useEffect, useMemo, useRef, useState } from "react";
import type { CitySummary, CityGroup, CityGroupsView, CityOverview } from "../types";
import {
  getCityGroups, getCitiesOverview, createCityGroup, patchCityGroup, deleteCityGroup,
  addCitiesToGroup, removeCitiesFromGroup,
} from "../api";

const titleCase = (s: string) => s ? s.charAt(0) + s.slice(1).toLowerCase() : s;

/** Small hook: load the player's groups + cities overview, with a refresh() for after mutations. */
export function useCityGroups() {
  const [view, setView] = useState<CityGroupsView | null>(null);
  const [overview, setOverview] = useState<CityOverview[]>([]);
  const refresh = () => {
    getCityGroups().then(setView).catch(() => {});
    getCitiesOverview().then(setOverview).catch(() => {});
  };
  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, []);
  return { view, overview, refresh };
}

/** Compact context line for a city in the switcher: island · under-attack · what's building. */
function CityMeta({ c }: { c?: CityOverview }) {
  if (!c) return null;
  return (
    <span className="cg-meta">
      <span className="cg-isl">{c.island}</span>
      {c.underAttack && <span className="cg-warn" title="Under attack">⚔ under attack</span>}
      {c.building && <span className="cg-build" title="Under construction">🔨 {titleCase(c.building.type)}{c.building.toLevel ? `→${c.building.toLevel}` : ""}</span>}
    </span>
  );
}

/**
 * The city-name switcher: ‹ prev · name ▾ · next › — the ▾ opens an at-a-glance panel that lays
 * every city out divided by group (with search + a group filter). Prev/next cycle within the
 * active group filter if one is set, else across all cities.
 */
export function CitySwitcher({ cities, groups, overview, activeId, activeName, onSwitch, onStartRename, onManage, onOpen }: {
  cities: CitySummary[]; groups: CityGroup[]; overview: CityOverview[];
  activeId: number; activeName: string;
  onSwitch: (id: number) => void; onStartRename: () => void; onManage: () => void; onOpen?: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<number | "all">("all");   // group id or "all"
  const wrapRef = useRef<HTMLDivElement>(null);

  const ovById = useMemo(() => new Map(overview.map(c => [c.id, c])), [overview]);
  // canonical order = overview order (capital first, then name) with a cities[] fallback
  const ordered = useMemo(() =>
    overview.length ? overview.map(c => c.id) : cities.map(c => c.id), [overview, cities]);

  // prev/next cycle within the current filter group when one is set
  const cycleIds = useMemo(() => {
    if (filter === "all") return ordered;
    const inGroup = new Set(overview.filter(c => c.groupIds.includes(filter)).map(c => c.id));
    const within = ordered.filter(id => inGroup.has(id));
    return within.length ? within : ordered;
  }, [ordered, overview, filter]);

  const step = (dir: 1 | -1) => {
    if (cycleIds.length < 2) return;
    let i = cycleIds.indexOf(activeId);
    if (i < 0) i = 0;
    onSwitch(cycleIds[(i + dir + cycleIds.length) % cycleIds.length]);
  };

  // close on outside click / Escape
  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => { if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false); };
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => { document.removeEventListener("mousedown", onDoc); document.removeEventListener("keydown", onKey); };
  }, [open]);

  const pick = (id: number) => { onSwitch(id); setOpen(false); setSearch(""); };

  const q = search.trim().toLowerCase();
  const match = (c: CityOverview) => !q || c.name.toLowerCase().includes(q);

  // build sections: one per group (respecting filter), then Ungrouped
  const shownGroups = filter === "all" ? groups : groups.filter(g => g.id === filter);
  const sections = shownGroups.map(g => ({
    key: `g${g.id}`, icon: g.icon, name: g.name,
    cities: overview.filter(c => c.groupIds.includes(g.id) && match(c)),
  }));
  const ungrouped = overview.filter(c => c.groupIds.length === 0 && match(c));
  const showUngrouped = filter === "all" && ungrouped.length > 0;

  return (
    <div className="cg-switch" ref={wrapRef}>
      <button className="cg-arrow" title="Previous city" onClick={() => step(-1)} disabled={cycleIds.length < 2}>‹</button>
      <button className="cg-title" onClick={() => setOpen(o => { const n = !o; if (n) onOpen?.(); return n; })} title="Show all cities">
        <span className="cg-title-name">{activeName}</span>
        <span className="cg-caret">{open ? "▴" : "▾"}</span>
      </button>
      <button className="cg-arrow" title="Next city" onClick={() => step(1)} disabled={cycleIds.length < 2}>›</button>
      <button className="cs-rename" title="Rename city" onClick={onStartRename}>✎</button>

      {open && (
        <div className="cg-panel">
          <div className="cg-panel-top">
            <input className="cg-search" autoFocus placeholder="Search cities…" value={search}
              onChange={e => setSearch(e.target.value)} />
            <button className="cg-manage" onClick={() => { setOpen(false); onManage(); }}>⚙ Manage</button>
          </div>

          <div className="cg-filters">
            <button className={"cg-chip" + (filter === "all" ? " on" : "")} onClick={() => setFilter("all")}>All</button>
            {groups.map(g => (
              <button key={g.id} className={"cg-chip" + (filter === g.id ? " on" : "")} onClick={() => setFilter(g.id)}>
                <span className="cg-chip-ico">{g.icon}</span>{g.name}</button>
            ))}
          </div>

          <div className="cg-sections">
            {sections.map(s => (
              <div className="cg-section" key={s.key}>
                <div className="cg-section-head"><span className="cg-section-ico">{s.icon}</span>{s.name}
                  <span className="cg-count">{s.cities.length}</span></div>
                {s.cities.length === 0
                  ? <div className="cg-empty-row">No cities{q ? " match" : ""}.</div>
                  : s.cities.map(c => (
                    <button key={c.id} className={"cg-city" + (c.id === activeId ? " active" : "")} onClick={() => pick(c.id)}>
                      <span className="cg-city-name">{c.capital ? "★ " : ""}{c.name}</span>
                      <CityMeta c={c} />
                    </button>
                  ))}
              </div>
            ))}
            {showUngrouped && (
              <div className="cg-section">
                <div className="cg-section-head"><span className="cg-section-ico">📍</span>Ungrouped
                  <span className="cg-count">{ungrouped.length}</span></div>
                {ungrouped.map(c => (
                  <button key={c.id} className={"cg-city" + (c.id === activeId ? " active" : "")} onClick={() => pick(c.id)}>
                    <span className="cg-city-name">{c.capital ? "★ " : ""}{c.name}</span>
                    <CityMeta c={c} />
                  </button>
                ))}
              </div>
            )}
            {sections.every(s => s.cities.length === 0) && !showUngrouped && (
              <div className="cg-empty">No cities match “{search}”.</div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/** Manage Groups: create/edit/delete/reorder groups + assign cities (multi-select, many-to-many). */
export function ManageGroupsPanel({ view, overview, onClose, onChanged, setErr }: {
  view: CityGroupsView | null; overview: CityOverview[];
  onClose: () => void; onChanged: () => void; setErr: (s: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [newName, setNewName] = useState("");
  const [newIcon, setNewIcon] = useState<string>("🏰");
  const [expanded, setExpanded] = useState<number | null>(null);
  const [editId, setEditId] = useState<number | null>(null);
  const [editName, setEditName] = useState("");

  const groups = view?.groups ?? [];
  const icons = view?.iconChoices ?? [];
  const memberOf = (gid: number) => new Set(overview.filter(c => c.groupIds.includes(gid)).map(c => c.id));

  const act = async (fn: () => Promise<any>) => {
    setErr(""); setBusy(true);
    try { await fn(); onChanged(); } catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };

  const create = () => {
    const n = newName.trim();
    if (!n) { setErr("Name the group first"); return; }
    act(() => createCityGroup(n, newIcon)).then(() => { setNewName(""); });
  };
  const toggleCity = (gid: number, cityId: number, isMember: boolean) =>
    act(() => isMember ? removeCitiesFromGroup(gid, [cityId]) : addCitiesToGroup(gid, [cityId]));
  const move = (g: CityGroup, dir: -1 | 1) => {
    const others = groups.filter(x => x.id !== g.id);
    const swap = groups[groups.indexOf(g) + dir];
    if (!swap) return;
    // swap sortOrder values
    act(async () => { await patchCityGroup(g.id, { sortOrder: swap.sortOrder }); await patchCityGroup(swap.id, { sortOrder: g.sortOrder }); });
    void others;
  };
  const saveEdit = (g: CityGroup) => {
    const n = editName.trim();
    setEditId(null);
    if (n && n !== g.name) act(() => patchCityGroup(g.id, { name: n }));
  };

  return (
    <div className="cg-manage-backdrop" onClick={onClose}>
      <div className="cg-manage" onClick={e => e.stopPropagation()}>
        <button className="cg-manage-close" onClick={onClose}>✕</button>
        <h2>City Groups</h2>
        <p className="cg-sub">Organize your cities into custom groups. A city can belong to several groups. Groups are for navigation only — they don't affect gameplay.</p>

        {/* create */}
        <div className="cg-create">
          <div className="cg-icon-picker">
            {icons.map(ic => (
              <button key={ic} className={"cg-icon-opt" + (ic === newIcon ? " on" : "")} onClick={() => setNewIcon(ic)}>{ic}</button>
            ))}
          </div>
          <div className="cg-create-row">
            <input className="cg-input" placeholder="New group name…" maxLength={40} value={newName}
              onChange={e => setNewName(e.target.value)} onKeyDown={e => { if (e.key === "Enter") create(); }} />
            <button className="cg-btn primary" disabled={busy} onClick={create}>Create</button>
          </div>
        </div>

        {/* group list */}
        <div className="cg-glist">
          {groups.length === 0 && <div className="cg-empty">No groups yet — create one above.</div>}
          {groups.map((g, i) => {
            const members = memberOf(g.id);
            return (
              <div className="cg-grow" key={g.id}>
                <div className="cg-grow-head">
                  <span className="cg-grow-ico">{g.icon}</span>
                  {editId === g.id
                    ? <input className="cg-input inline" autoFocus value={editName} maxLength={40}
                        onChange={e => setEditName(e.target.value)} onBlur={() => saveEdit(g)}
                        onKeyDown={e => { if (e.key === "Enter") saveEdit(g); if (e.key === "Escape") setEditId(null); }} />
                    : <button className="cg-grow-name" onClick={() => { setEditId(g.id); setEditName(g.name); }} title="Rename">{g.name}</button>}
                  <span className="cg-count">{members.size}</span>
                  <span className="cg-grow-actions">
                    <button className="cg-ic-btn" disabled={busy || i === 0} title="Move up" onClick={() => move(g, -1)}>↑</button>
                    <button className="cg-ic-btn" disabled={busy || i === groups.length - 1} title="Move down" onClick={() => move(g, 1)}>↓</button>
                    <button className="cg-ic-btn" title="Assign cities" onClick={() => setExpanded(expanded === g.id ? null : g.id)}>{expanded === g.id ? "▴" : "▾"}</button>
                    <button className="cg-ic-btn danger" disabled={busy} title="Delete group" onClick={() => act(() => deleteCityGroup(g.id))}>🗑</button>
                  </span>
                </div>
                {/* icon change */}
                {expanded === g.id && (
                  <div className="cg-assign">
                    <div className="cg-icon-picker small">
                      {icons.map(ic => (
                        <button key={ic} className={"cg-icon-opt" + (ic === g.icon ? " on" : "")} disabled={busy}
                          onClick={() => act(() => patchCityGroup(g.id, { icon: ic }))}>{ic}</button>
                      ))}
                    </div>
                    <div className="cg-assign-grid">
                      {overview.map(c => {
                        const isMember = members.has(c.id);
                        return (
                          <label key={c.id} className={"cg-assign-city" + (isMember ? " on" : "")}>
                            <input type="checkbox" checked={isMember} disabled={busy}
                              onChange={() => toggleCity(g.id, c.id, isMember)} />
                            {c.capital ? "★ " : ""}{c.name}
                          </label>
                        );
                      })}
                      {overview.length === 0 && <div className="cg-empty">No cities.</div>}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
