package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.domain.City;
import com.polis.domain.Player;
import com.polis.game.RankingService;
import com.polis.repo.AllianceRepo;
import com.polis.repo.CityRepo;
import com.polis.repo.PlayerRepo;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** Public player profile — the card shown when clicking a name in the rankings. */
@RestController
@RequestMapping("/api/players")
public class ProfileController {
  private final PlayerRepo players; private final CityRepo cities;
  private final AllianceRepo alliances; private final RankingService ranking;

  public ProfileController(PlayerRepo players, CityRepo cities, AllianceRepo alliances, RankingService ranking){
    this.players = players; this.cities = cities; this.alliances = alliances; this.ranking = ranking;
  }

  @GetMapping("/{id}/profile")
  public Map<String,Object> profile(@PathVariable Long id){
    Player me = players.findById(SecurityConfig.currentPlayerId()).orElseThrow();
    Player p = players.findById(id).orElseThrow(() -> new IllegalArgumentException("Player not found"));
    // same world only — profiles are per-world
    if (!Objects.equals(p.getWorldId(), me.getWorldId())) throw new IllegalStateException("Player is in another world");

    String alliance = null;
    if (p.getAllianceId() != null)
      alliance = alliances.findById(p.getAllianceId())
        .map(a -> a.getTag() != null ? a.getName() + " [" + a.getTag() + "]" : a.getName()).orElse(null);

    List<Map<String,Object>> cityList = new ArrayList<>();
    List<City> owned = cities.findByPlayerId(id);
    owned.sort(Comparator.comparing(City::getName, Comparator.nullsLast(String::compareTo)));
    for (City c : owned){
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("id", c.getId()); m.put("name", c.getName()); m.put("points", c.getPoints());
      m.put("raceName", c.getRace() == null ? null : c.getRace().displayName);
      m.put("capital", c.isCapital());
      cityList.add(m);
    }

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("id", p.getId());
    out.put("username", p.getUsername());
    out.put("level", p.getLevel());
    out.put("totalPoints", ranking.playerPoints(id));
    out.put("combatPoints", p.getCombatPointsTotal());
    out.put("alliance", alliance);
    out.put("pointsRank", ranking.pointsRankOf(p.getWorldId(), id));
    out.put("combatRank", ranking.combatRankOf(p.getWorldId(), id));
    out.put("cities", cityList);
    return out;
  }
}
