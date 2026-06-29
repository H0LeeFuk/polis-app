package com.polis.game;

import com.polis.domain.Player;
import com.polis.repo.BattleReportRepo;
import com.polis.repo.CityRepo;
import com.polis.repo.PlayerRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Account-wide city-expansion progression: Culture Points raise the player's level (each level =
 * +1 city slot, cap 20), and combat victories grant a spendable Combat-Points currency used as
 * festival fuel — with anti-farming so stomping weak/repeat targets is worthless.
 */
@Service
public class ProgressionService {
  private final PlayerRepo players;
  private final CityRepo cities;
  private final BattleReportRepo reports;

  public ProgressionService(PlayerRepo players, CityRepo cities, BattleReportRepo reports){
    this.players = players; this.cities = cities; this.reports = reports;
  }

  /** Add Culture Points and apply any level-ups (supports crossing several thresholds at once). */
  @Transactional
  public void grantCulture(Long playerId, int amount){
    if (playerId == null || amount <= 0) return;
    Player p = players.findById(playerId).orElse(null);
    if (p == null) return;
    p.setCulturePoints(p.getCulturePoints() + amount);
    p.setCulturePointsTotal(p.getCulturePointsTotal() + amount);
    while (p.getLevel() < GameRules.MAX_LEVEL){
      int need = GameRules.cultureForLevel(p.getLevel() + 1);
      if (p.getCulturePoints() < need) break;
      p.setCulturePoints(p.getCulturePoints() - need);
      p.setLevel(p.getLevel() + 1);          // +1 city slot unlocked
    }
    players.save(p);
  }

  /** Combat-Points awarded for a win, after anti-farming. */
  public record CombatAward(int points, String reason){}

  /**
   * Compute the (anti-farmed) Combat Points the {@code winnerId} earns for beating {@code loser}.
   * Zero from NPC/barbarian/self/alliance targets; scaled down for weaker and repeat targets.
   */
  public CombatAward combatAward(Long winnerId, Player loser, int killedPop, double winnerPower, double loserPower){
    if (winnerId == null) return new CombatAward(0, null);
    if (loser == null) return new CombatAward(0, "NPC / barbarian target — no Combat Points");
    if (winnerId.equals(loser.getId())) return new CombatAward(0, "Own target — no Combat Points");
    Player winner = players.findById(winnerId).orElse(null);
    if (winner != null && winner.getAllianceId() != null && winner.getAllianceId().equals(loser.getAllianceId()))
      return new CombatAward(0, "Alliance member — no Combat Points");
    int base = Math.max(0, killedPop);                 // bigger real fights → more points
    if (base == 0) return new CombatAward(0, null);
    List<String> notes = new ArrayList<>();
    // strength parity: full value only vs a comparable target; stomping the weak → near 0
    double parity = winnerPower <= 0 ? 1.0 : Math.min(1.0, loserPower / Math.max(1.0, winnerPower));
    if (parity < 0.9) notes.add("Much weaker target: −" + Math.round((1 - parity) * 100) + "%");
    // diminishing returns vs the SAME player over a rolling 24h window
    long recent = reports.countRecentWins(winnerId, loser.getId(), Instant.now().minus(Duration.ofHours(24)));
    double dim = Math.pow(0.5, recent);
    if (recent > 0) notes.add((recent + 1) + nth(recent + 1) + " win vs this player (24h): −" + Math.round((1 - dim) * 100) + "%");
    int pts = (int) Math.round(base * parity * dim);
    return new CombatAward(pts, notes.isEmpty() ? null : String.join(" · ", notes));
  }

  private static String nth(long n){ long m = n % 10, h = n % 100; return (m==1&&h!=11)?"st":(m==2&&h!=12)?"nd":(m==3&&h!=13)?"rd":"th"; }

  /** Account progression snapshot for the UI. */
  public Map<String,Object> progression(Long playerId){
    Player p = players.findById(playerId).orElseThrow(() -> new IllegalArgumentException("Player not found"));
    boolean atMax = p.getLevel() >= GameRules.MAX_LEVEL;
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("level", p.getLevel());
    m.put("maxLevel", GameRules.MAX_LEVEL);
    m.put("culturePoints", p.getCulturePoints());
    m.put("cultureForNextLevel", atMax ? null : GameRules.cultureForLevel(p.getLevel() + 1));
    m.put("culturePointsTotal", p.getCulturePointsTotal());
    m.put("combatPoints", p.getCombatPoints());
    m.put("citiesOwned", (int) cities.countByPlayerId(playerId));
    m.put("maxCities", GameRules.maxCities(p.getLevel()));
    m.put("cap", GameRules.MAX_LEVEL);
    m.put("atMax", atMax);
    return m;
  }
}
