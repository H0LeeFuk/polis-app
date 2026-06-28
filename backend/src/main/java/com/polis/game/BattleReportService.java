package com.polis.game;

import com.polis.api.BattleReportDTO;
import com.polis.api.BattleReportSummaryDTO;
import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Persists and serves Battle Reports. {@link #createReport} is called by the combat
 * resolver after a raid is decided; everything else is read/flag mutation only —
 * reports are immutable except for per-perspective read/deleted flags.
 */
@Service
public class BattleReportService {
  private final BattleReportRepo reports;
  private final CityRepo cities;
  private final PlayerRepo players;

  public BattleReportService(BattleReportRepo reports, CityRepo cities, PlayerRepo players){
    this.reports = reports; this.cities = cities; this.players = players;
  }

  // --- creation (called from TickScheduler within the tick transaction) --------

  @Transactional
  public void createReport(Movement m, BattleResult res){ createReport(m, res, null); }

  @Transactional
  public void createReport(Movement m, BattleResult res, HeroParticipation hero){
    City atkCity = cities.findById(m.getSourceCityId()).orElse(null);
    City defCity = cities.findById(m.getTargetCityId()).orElse(null);
    Long defPlayerId = defCity != null ? defCity.getPlayerId() : null;

    BattleReport r = new BattleReport();
    r.setWorldId(m.getWorldId());
    r.setMovementId(m.getId());
    r.setOutcome(res.outcome());

    r.setAttackerPlayerId(m.getPlayerId());
    r.setAttackerCityId(m.getSourceCityId());
    r.setAttackerCityName(atkCity != null ? atkCity.getName() : "Unknown city");
    r.setAttackerPlayerName(playerName(m.getPlayerId()));

    r.setDefenderPlayerId(defPlayerId);
    r.setDefenderCityId(m.getTargetCityId());
    r.setDefenderCityName(defCity != null ? defCity.getName() : "Unknown city");
    r.setDefenderPlayerName(defPlayerId != null ? playerName(defPlayerId) : null);

    r.setAttackerTroopsSent(new HashMap<>(res.attackerSent()));
    r.setAttackerTroopsLost(new HashMap<>(res.attackerLost()));
    r.setAttackerTroopsSurvived(new HashMap<>(res.attackerSurvived()));
    r.setDefenderTroopsPresent(new HashMap<>(res.defenderPresent()));
    r.setDefenderTroopsLost(new HashMap<>(res.defenderLost()));
    r.setDefenderTroopsSurvived(new HashMap<>(res.defenderSurvived()));
    r.setResourcesStolen(new HashMap<>(res.resourcesStolen()));

    r.setAttackerTotalAttackPower(res.attackerAttackPower());
    r.setDefenderTotalDefencePower(res.defenderDefencePower());
    r.setSiegeDamage(res.siegeDamage());

    if (hero != null){
      r.setHeroName(hero.name());
      r.setHeroLevel(hero.level());
      r.setHeroAttackBonusPct(hero.attackBonusPct());
      r.setHeroLossReductionPct(hero.lossReductionPct());
      r.setHeroSkillUsed(hero.skillUsed());
      r.setHeroXpGained(hero.xpGained());
      r.setHeroLeveledTo(hero.leveledTo());
      r.setHeroWounded(hero.wounded());
    }
    reports.save(r);
  }

  /** Report for a resource-node battle: the "defender city" is the node itself. */
  @Transactional
  public void createNodeReport(Movement m, BattleResult res, HeroParticipation hero,
                               String nodeName, Long defenderPlayerId){
    City atkCity = cities.findById(m.getSourceCityId()).orElse(null);
    BattleReport r = new BattleReport();
    r.setWorldId(m.getWorldId());
    r.setMovementId(m.getId());
    r.setOutcome(res.outcome());
    r.setAttackerPlayerId(m.getPlayerId());
    r.setAttackerCityId(m.getSourceCityId());
    r.setAttackerCityName(atkCity != null ? atkCity.getName() : "Unknown city");
    r.setAttackerPlayerName(playerName(m.getPlayerId()));
    r.setDefenderPlayerId(defenderPlayerId);
    r.setDefenderCityId(null);
    r.setDefenderCityName(nodeName);
    r.setDefenderPlayerName(defenderPlayerId != null ? playerName(defenderPlayerId) : null);
    r.setAttackerTroopsSent(new HashMap<>(res.attackerSent()));
    r.setAttackerTroopsLost(new HashMap<>(res.attackerLost()));
    r.setAttackerTroopsSurvived(new HashMap<>(res.attackerSurvived()));
    r.setDefenderTroopsPresent(new HashMap<>(res.defenderPresent()));
    r.setDefenderTroopsLost(new HashMap<>(res.defenderLost()));
    r.setDefenderTroopsSurvived(new HashMap<>(res.defenderSurvived()));
    r.setResourcesStolen(new HashMap<>(res.resourcesStolen()));
    r.setAttackerTotalAttackPower(res.attackerAttackPower());
    r.setDefenderTotalDefencePower(res.defenderDefencePower());
    r.setSiegeDamage(res.siegeDamage());
    if (hero != null){
      r.setHeroName(hero.name()); r.setHeroLevel(hero.level());
      r.setHeroAttackBonusPct(hero.attackBonusPct()); r.setHeroLossReductionPct(hero.lossReductionPct());
      r.setHeroSkillUsed(hero.skillUsed()); r.setHeroXpGained(hero.xpGained());
      r.setHeroLeveledTo(hero.leveledTo()); r.setHeroWounded(hero.wounded());
    }
    reports.save(r);
  }

  private String playerName(Long id){
    if (id == null) return "Barbarians";
    return players.findById(id).map(Player::getUsername).orElse("Unknown");
  }

  // --- queries -----------------------------------------------------------------

  @Transactional(readOnly = true)
  public Map<String,Object> list(Long me, BattleOutcome outcome, Boolean read, Long cityId, int page, int size){
    Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
    Page<BattleReport> result = reports.search(me, outcome, cityId, read, pageable);

    List<BattleReportSummaryDTO> content = new ArrayList<>();
    for (BattleReport r : result.getContent()) content.add(toSummary(r, me));

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("content", content);
    out.put("page", result.getNumber());
    out.put("size", result.getSize());
    out.put("totalElements", result.getTotalElements());
    out.put("totalPages", result.getTotalPages());
    out.put("hasMore", result.getNumber() + 1 < result.getTotalPages());
    return out;
  }

  /** Same as {@link #list} but scoped to a city the player owns. */
  @Transactional(readOnly = true)
  public Map<String,Object> cityList(Long me, Long cityId, BattleOutcome outcome, Boolean read, int page, int size){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), me)) throw new IllegalStateException("Not your city");
    return list(me, outcome, read, cityId, page, size);
  }

  @Transactional
  public BattleReportDTO get(Long me, Long id){
    BattleReport r = reports.findById(id).orElseThrow(() -> new IllegalArgumentException("Report not found"));
    boolean attacker = ensureParty(r, me);
    if (attacker) r.setAttackerRead(true); else r.setDefenderRead(true);
    reports.save(r);
    return toFull(r, me);
  }

  @Transactional
  public void delete(Long me, Long id){
    BattleReport r = reports.findById(id).orElseThrow(() -> new IllegalArgumentException("Report not found"));
    boolean attacker = ensureParty(r, me);
    if (attacker) r.setAttackerDeleted(true); else r.setDefenderDeleted(true);
    reports.save(r);
  }

  @Transactional
  public void markAllRead(Long me){
    reports.markAllReadAsAttacker(me);
    reports.markAllReadAsDefender(me);
  }

  @Transactional(readOnly = true)
  public long unreadCount(Long me){ return reports.countUnread(me); }

  // --- helpers -----------------------------------------------------------------

  /** @return true if the player is the attacker; throws if they are not a party at all. */
  private boolean ensureParty(BattleReport r, Long me){
    if (Objects.equals(r.getAttackerPlayerId(), me)) return true;
    if (Objects.equals(r.getDefenderPlayerId(), me)) return false;
    throw new IllegalStateException("Not your report");
  }

  private boolean isAttacker(BattleReport r, Long me){ return Objects.equals(r.getAttackerPlayerId(), me); }
  private boolean unread(BattleReport r, boolean attacker){ return attacker ? !r.isAttackerRead() : !r.isDefenderRead(); }
  private static int sum(Map<String,Integer> m){ int s = 0; if (m != null) for (int v : m.values()) s += v; return s; }

  private BattleReportSummaryDTO toSummary(BattleReport r, Long me){
    boolean attacker = isAttacker(r, me);
    return new BattleReportSummaryDTO(
        r.getId(), r.getFoughtAt().toString(), r.getOutcome().name(), attacker ? "ATTACKER" : "DEFENDER",
        r.getAttackerCityId(), r.getAttackerCityName(),
        r.getDefenderCityId(), r.getDefenderCityName(),
        r.getAttackerPlayerName(), r.getDefenderPlayerName(),
        sum(r.getAttackerTroopsSent()), sum(r.getAttackerTroopsLost()), sum(r.getDefenderTroopsLost()),
        r.getResourcesStolen(), unread(r, attacker));
  }

  private BattleReportDTO toFull(BattleReport r, Long me){
    boolean attacker = isAttacker(r, me);
    return new BattleReportDTO(
        r.getId(), r.getFoughtAt().toString(), r.getOutcome().name(), attacker ? "ATTACKER" : "DEFENDER",
        r.getAttackerPlayerId(), r.getAttackerPlayerName(), r.getAttackerCityId(), r.getAttackerCityName(),
        r.getDefenderPlayerId(), r.getDefenderPlayerName(), r.getDefenderCityId(), r.getDefenderCityName(),
        r.getAttackerTroopsSent(), r.getAttackerTroopsLost(), r.getAttackerTroopsSurvived(),
        r.getDefenderTroopsPresent(), r.getDefenderTroopsLost(), r.getDefenderTroopsSurvived(),
        r.getResourcesStolen(), r.getAttackerTotalAttackPower(), r.getDefenderTotalDefencePower(),
        r.getSiegeDamage(),
        r.getHeroName(), r.getHeroLevel(), r.getHeroAttackBonusPct(), r.getHeroLossReductionPct(),
        r.getHeroSkillUsed(), r.getHeroXpGained(), r.getHeroLeveledTo(), r.isHeroWounded(),
        unread(r, attacker));
  }
}
