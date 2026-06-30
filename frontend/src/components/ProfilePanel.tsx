import { useEffect, useMemo, useState } from "react";
import { getRankings, getMyAlliance } from "../api";
import { useDraggable } from "../useDraggable";
import type { PlayerDto, CitySummary } from "../types";

const fmt = (n: number) => n.toLocaleString("en-US");

// Rank of the current player inside an ordered ranking list (1-based), or null if absent.
function rankOf(rows: { name: string }[], name: string): number | null {
  const i = rows.findIndex(r => r.name === name);
  return i < 0 ? null : i + 1;
}

const initials = (name: string) =>
  name.trim().split(/\s+/).map(w => w[0]).join("").slice(0, 2).toUpperCase() || "?";

export default function ProfilePanel({ player, cities, faction, onClose }: {
  player: PlayerDto; cities: CitySummary[]; faction: string; onClose: () => void;
}) {
  const [pointsRank, setPointsRank] = useState<number | null>(null);
  const [combatRank, setCombatRank] = useState<number | null>(null);
  const [allianceLabel, setAllianceLabel] = useState<string | null>(player.alliance ?? null);
  const win = useDraggable<HTMLDivElement>();

  useEffect(() => {
    let live = true;
    getRankings("points").then(r => { if (live) setPointsRank(rankOf(r, player.username)); }).catch(() => {});
    getRankings("combat").then(r => { if (live) setCombatRank(rankOf(r, player.username)); }).catch(() => {});
    if (player.alliance) {
      getMyAlliance()
        .then(a => { if (live && a.inAlliance && a.name) setAllianceLabel(a.tag ? `${a.name} [${a.tag}]` : a.name); })
        .catch(() => {});
    }
    return () => { live = false; };
  }, [player.username, player.alliance]);

  const sorted = useMemo(
    () => [...cities].sort((a, b) => a.name.localeCompare(b.name)),
    [cities]
  );
  const rankStr = (r: number | null) => r == null ? "#—" : "#" + fmt(r);

  return (
    <div className="profile-backdrop" onClick={onClose}>
      <div className="profile-card" ref={win} onClick={e => e.stopPropagation()}>
        <button className="profile-close" onClick={onClose}>✕</button>

        <header className="profile-head">
          <div className="profile-avatar">
            <span className="profile-avatar-ph">{initials(player.username)}</span>
            <span className="profile-level-badge">{player.level}</span>
          </div>
          <div className="profile-id">
            <h2 className="profile-name">{player.username}</h2>
            <div className="profile-sub">Level {player.level}</div>
            {allianceLabel && (
              <span className="profile-ally">
                <svg className="profile-ally-ico" viewBox="0 0 24 24" width="13" height="13" aria-hidden="true">
                  <path fill="currentColor" d="M12 2 4 5v6c0 5 3.4 8.5 8 11 4.6-2.5 8-6 8-11V5l-8-3Z" />
                </svg>
                {allianceLabel}
              </span>
            )}
          </div>
        </header>

        <div className="profile-ranks">
          <div className="profile-rank-tile">
            <div className="profile-rank-label">Points rank</div>
            <div className="profile-rank-value pts">{rankStr(pointsRank)}</div>
            <div className="profile-rank-sub">{fmt(player.totalPoints)} points</div>
          </div>
          <div className="profile-rank-tile">
            <div className="profile-rank-label">Combat rank</div>
            <div className="profile-rank-value cbt">{rankStr(combatRank)}</div>
            <div className="profile-rank-sub">{fmt(player.combatPoints)} combat pts</div>
          </div>
        </div>

        <div className="profile-cities-head">
          <span className="profile-bullet" />
          <span className="profile-cities-title">MY CITIES</span>
          <span className="profile-cities-count">{sorted.length}</span>
          <span className="profile-cities-total">{fmt(player.totalPoints)} pts total</span>
        </div>

        <div className="profile-cities">
          {sorted.map(c => (
            <div className="profile-city-row" key={c.id}>
              <svg className="profile-city-ico" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                <path fill="currentColor" d="M4 21V9l4-3 4 3V5l4-3 4 3v16H4Zm2-2h4v-4H6v4Zm0-6h4V9.9L8 8.4 6 9.9V13Zm8 6h4V6.2l-2-1.5-2 1.5V11h-2v8h2v-2h2v2Z" />
              </svg>
              <span className="profile-city-name">{c.name}</span>
              {c.raceName && <span className="profile-city-race">{c.raceName}</span>}
              {c.capital && <span className="profile-city-cap">Capital</span>}
              <span className="profile-city-spacer" />
              <span className="profile-city-pts">{fmt(c.points)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
