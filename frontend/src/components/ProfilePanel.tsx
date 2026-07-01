import { useEffect, useMemo, useState } from "react";
import { getRankings, getMyAlliance, getPlayerProfile } from "../api";
import { useDraggable } from "../useDraggable";
import type { PlayerDto, CitySummary, PublicProfile } from "../types";

const fmt = (n: number) => n.toLocaleString("en-US");

// Rank of the current player inside an ordered ranking list (1-based), or null if absent.
function rankOf(rows: { name: string }[], name: string): number | null {
  const i = rows.findIndex(r => r.name === name);
  return i < 0 ? null : i + 1;
}

const initials = (name: string) =>
  name.trim().split(/\s+/).map(w => w[0]).join("").slice(0, 2).toUpperCase() || "?";

type CityRow = { id: number; name: string; points: number; raceName: string | null; capital: boolean };

/**
 * Player profile card. Shows the logged-in player when passed {@code player}+{@code cities}, or another
 * player's public profile when passed {@code playerId} (e.g. clicking a name in the rankings).
 */
export default function ProfilePanel({ player, cities, onClose, playerId, onGoCity }: {
  player?: PlayerDto; cities?: CitySummary[]; faction?: string; onClose: () => void; playerId?: number;
  onGoCity?: (cityId: number) => void;
}) {
  const isSelf = playerId == null;
  const [pub, setPub] = useState<PublicProfile | null>(null);
  const [pointsRank, setPointsRank] = useState<number | null>(null);
  const [combatRank, setCombatRank] = useState<number | null>(null);
  const [allianceLabel, setAllianceLabel] = useState<string | null>(isSelf ? (player?.alliance ?? null) : null);
  const win = useDraggable<HTMLDivElement>();

  useEffect(() => {
    let live = true;
    if (isSelf && player) {
      getRankings("points").then(r => { if (live) setPointsRank(rankOf(r, player.username)); }).catch(() => {});
      getRankings("combat").then(r => { if (live) setCombatRank(rankOf(r, player.username)); }).catch(() => {});
      if (player.alliance) {
        getMyAlliance()
          .then(a => { if (live && a.inAlliance && a.name) setAllianceLabel(a.tag ? `${a.name} [${a.tag}]` : a.name); })
          .catch(() => {});
      }
    } else if (playerId != null) {
      getPlayerProfile(playerId).then(p => {
        if (!live) return;
        setPub(p); setPointsRank(p.pointsRank); setCombatRank(p.combatRank); setAllianceLabel(p.alliance);
      }).catch(() => {});
    }
    return () => { live = false; };
  }, [isSelf, player?.username, player?.alliance, playerId]);

  // unified display model (self vs fetched public profile)
  const username = isSelf ? (player?.username ?? "") : (pub?.username ?? "…");
  const level = isSelf ? (player?.level ?? 0) : (pub?.level ?? 0);
  const totalPoints = isSelf ? (player?.totalPoints ?? 0) : (pub?.totalPoints ?? 0);
  const combatPoints = isSelf ? (player?.combatPoints ?? 0) : (pub?.combatPoints ?? 0);
  const cityRows: CityRow[] = isSelf
    ? (cities ?? []).map(c => ({ id: c.id, name: c.name, points: c.points, raceName: c.raceName ?? null, capital: c.capital }))
    : (pub?.cities ?? []);

  const sorted = useMemo(() => [...cityRows].sort((a, b) => a.name.localeCompare(b.name)), [cityRows]);
  const rankStr = (r: number | null) => r == null ? "#—" : "#" + fmt(r);

  return (
    <div className="profile-backdrop" onClick={onClose}>
      <div className="profile-card" ref={win} onClick={e => e.stopPropagation()}>
        <button className="profile-close" onClick={onClose}>✕</button>

        <header className="profile-head">
          <div className="profile-avatar">
            <span className="profile-avatar-ph">{initials(username)}</span>
            <span className="profile-level-badge">{level}</span>
          </div>
          <div className="profile-id">
            <h2 className="profile-name">{username}</h2>
            <div className="profile-sub">Level {level}</div>
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
            <div className="profile-rank-sub">{fmt(totalPoints)} points</div>
          </div>
          <div className="profile-rank-tile">
            <div className="profile-rank-label">Combat rank</div>
            <div className="profile-rank-value cbt">{rankStr(combatRank)}</div>
            <div className="profile-rank-sub">{fmt(combatPoints)} combat pts</div>
          </div>
        </div>

        <div className="profile-cities-head">
          <span className="profile-bullet" />
          <span className="profile-cities-title">{isSelf ? "MY CITIES" : "CITIES"}</span>
          <span className="profile-cities-count">{sorted.length}</span>
          <span className="profile-cities-total">{fmt(totalPoints)} pts total</span>
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
              {onGoCity && (
                <button className="profile-city-goto" title="Show on the world map"
                  onClick={() => onGoCity(c.id)}>🗺</button>
              )}
              <span className="profile-city-spacer" />
              <span className="profile-city-pts">{fmt(c.points)}</span>
            </div>
          ))}
          {sorted.length === 0 && <div className="profile-city-row muted">No cities.</div>}
        </div>
      </div>
    </div>
  );
}
