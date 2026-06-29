package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RankingService {
  private final PlayerRepo players; private final CityRepo cities; private final AllianceRepo alliances;
  public RankingService(PlayerRepo players, CityRepo cities, AllianceRepo alliances){
    this.players=players; this.cities=cities; this.alliances=alliances;
  }

  public record Row(String name, long value, String sub){}

  public int playerPoints(Long playerId){
    return cities.findByPlayerId(playerId).stream().mapToInt(City::getPoints).sum();
  }

  public List<Row> byPoints(Long worldId){
    return players.findByWorldId(worldId).stream()
      .map(p -> new Row(p.getUsername(), playerPoints(p.getId()),
          cities.countByPlayerId(p.getId())+" cities"))
      .sorted(Comparator.comparingLong(Row::value).reversed()).limit(50).toList();
  }
  public List<Row> byCombat(Long worldId){
    // value is LIFETIME Combat Points earned (never reduced by festival spending), so rankings
    // reflect total war prowess; ties broken by level so higher-level players rank above equals.
    return players.findByWorldId(worldId).stream()
      .sorted(Comparator.comparingInt(Player::getCombatPointsTotal).thenComparingInt(Player::getLevel).reversed())
      .map(p -> new Row(p.getUsername(), p.getCombatPointsTotal(), "level "+p.getLevel()))
      .limit(50).toList();
  }
  /** Rank alliances by the SUM of their members' Combat Points (alliances store no CP of their own). */
  public List<Row> alliancesByCombat(Long worldId){
    Map<Long,long[]> agg = new HashMap<>(); // allianceId -> [members, combatPoints]
    for (Player p : players.findByWorldId(worldId)){
      if (p.getAllianceId()==null) continue;
      long[] a = agg.computeIfAbsent(p.getAllianceId(), k->new long[2]);
      a[0]++; a[1]+=p.getCombatPointsTotal();
    }
    Map<Long,Alliance> byId = alliances.findByWorldId(worldId).stream().collect(Collectors.toMap(Alliance::getId, x->x));
    return agg.entrySet().stream().map(e -> {
        Alliance al = byId.get(e.getKey());
        long members=e.getValue()[0], cp=e.getValue()[1];
        return new Row(al!=null?al.getName():"—", cp, members+" members");
      }).sorted(Comparator.comparingLong(Row::value).reversed()).toList();
  }
  public List<Row> alliancesBy(Long worldId, boolean byPoints){
    Map<Long,long[]> agg = new HashMap<>(); // allianceId -> [members, points]
    for (Player p : players.findByWorldId(worldId)){
      if (p.getAllianceId()==null) continue;
      long[] a = agg.computeIfAbsent(p.getAllianceId(), k->new long[2]);
      a[0]++; a[1]+=playerPoints(p.getId());
    }
    Map<Long,Alliance> byId = alliances.findByWorldId(worldId).stream().collect(Collectors.toMap(Alliance::getId, x->x));
    return agg.entrySet().stream().map(e -> {
        Alliance al = byId.get(e.getKey());
        long members=e.getValue()[0], pts=e.getValue()[1];
        return new Row(al!=null?al.getName():"—", byPoints?pts:members, byPoints? members+" members" : pts+" pts");
      }).sorted(Comparator.comparingLong(Row::value).reversed()).toList();
  }
}
