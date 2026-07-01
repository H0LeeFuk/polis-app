package com.polis.game;

import com.polis.domain.AllianceTierProgress;
import com.polis.repo.AllianceTierProgressRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alliance Tier Gate. To found/conquer in a tier, an alliance must have earned enough progress in the
 * PREVIOUS tier: a number of island-boss kills AND accumulated resource-building control hours.
 * Tier-1 progress gates Tier 2; Tier-2 progress gates Tier 3. Tier 1 is always open.
 */
@Service
public class AllianceTierService {
  private final AllianceTierProgressRepo progress;
  public AllianceTierService(AllianceTierProgressRepo progress){ this.progress = progress; }

  // thresholds: [_, gate for T2 from T1, gate for T3 from T2]
  public static final int[]  REQ_BOSS_KILLS   = { 0, 5, 10 };
  public static final long[] REQ_CONTROL_SECS = { 0, 10L*3600, 25L*3600 };

  private AllianceTierProgress row(Long allianceId, int tier){
    return progress.findByAllianceIdAndTier(allianceId, tier)
        .orElseGet(() -> progress.save(new AllianceTierProgress(allianceId, tier)));
  }

  /** Credit a boss kill on a tier's island to the participating alliance. */
  @Transactional
  public void creditBossKill(Long allianceId, int tier){
    if (allianceId == null || tier < 1 || tier > 3) return;
    AllianceTierProgress p = row(allianceId, tier);
    p.setBossKills(p.getBossKills() + 1);
    progress.save(p);
  }

  /** Accrue resource-building control time on a tier (parallel-stacking across buildings). */
  @Transactional
  public void creditControlSeconds(Long allianceId, int tier, long seconds){
    if (allianceId == null || tier < 1 || tier > 3 || seconds <= 0) return;
    AllianceTierProgress p = row(allianceId, tier);
    p.setControlSeconds(p.getControlSeconds() + seconds);
    progress.save(p);
  }

  /** Can this alliance act (found/conquer) in {@code targetTier}? Tier 1 is always open. */
  @Transactional(readOnly = true)
  public boolean canActInTier(Long allianceId, int targetTier){
    if (targetTier <= 1) return true;
    if (allianceId == null) return false;
    int prev = targetTier - 1;
    AllianceTierProgress p = progress.findByAllianceIdAndTier(allianceId, prev).orElse(null);
    int kills = p == null ? 0 : p.getBossKills();
    long secs = p == null ? 0 : p.getControlSeconds();
    return kills >= REQ_BOSS_KILLS[prev] && secs >= REQ_CONTROL_SECS[prev];
  }

  /** Human-readable "what's left" for the gate into {@code targetTier} (2 or 3). */
  public String gateReason(Long allianceId, int targetTier){
    if (allianceId == null) return "Join an alliance — Tier 2/3 expansion is gated per alliance.";
    int prev = targetTier - 1;
    AllianceTierProgress p = progress.findByAllianceIdAndTier(allianceId, prev).orElse(null);
    int kills = p == null ? 0 : p.getBossKills();
    long hours = (p == null ? 0 : p.getControlSeconds()) / 3600;
    return String.format("Unlock Tier %d: defeat %d/%d Tier-%d bosses and hold Tier-%d resource buildings for %d/%dh.",
        targetTier, kills, REQ_BOSS_KILLS[prev], prev, prev, hours, REQ_CONTROL_SECS[prev] / 3600);
  }

  /** Full progress view for the alliance panel. */
  @Transactional(readOnly = true)
  public Map<String,Object> view(Long allianceId){
    Map<String,Object> out = new LinkedHashMap<>();
    for (int tier = 1; tier <= 2; tier++){   // progress in T1 (→T2) and T2 (→T3)
      AllianceTierProgress p = allianceId == null ? null : progress.findByAllianceIdAndTier(allianceId, tier).orElse(null);
      int nextTier = tier + 1;
      Map<String,Object> t = new LinkedHashMap<>();
      t.put("tier", tier);
      t.put("unlocksTier", nextTier);
      t.put("bossKills", p == null ? 0 : p.getBossKills());
      t.put("bossKillsRequired", REQ_BOSS_KILLS[tier]);
      t.put("controlHours", Math.round((p == null ? 0 : p.getControlSeconds()) / 360.0) / 10.0);
      t.put("controlHoursRequired", REQ_CONTROL_SECS[tier] / 3600);
      t.put("unlocked", canActInTier(allianceId, nextTier));
      out.put("tier" + tier, t);
    }
    out.put("tier2Unlocked", canActInTier(allianceId, 2));
    out.put("tier3Unlocked", canActInTier(allianceId, 3));
    return out;
  }
}
