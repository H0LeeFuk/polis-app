package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Read + action service for the Troop Detail panel:
 *  • "My Troops Abroad" — this player's troops stationed away (ally/other cities, resource nodes,
 *    sieges) and the recall action.
 *  • "Foreign Troops Here" — other players' reinforcements in one of my cities and the dismiss action.
 *
 * Recall/dismiss reuse RETURN {@link Movement}s (deposit on arrival at {@code sourceCityId}) and
 * {@link TravelTimeService} for the ETA, so they show on the map lines and movements panel like any march.
 *
 * <p>NOTE: reinforcements track only (hostCity, ownerPlayer) — not the origin city — so "abroad" is
 * scoped to the PLAYER (all of the player's away troops), not strictly to the active city. Node and
 * siege recalls delegate to the existing withdraw flows (troops return to the player's capital).
 */
@Service
public class TroopDetailService {
  private final CityRepo cities;
  private final PlayerRepo players;
  private final AllianceRepo alliances;
  private final ReinforcementRepo reinforcements;
  private final ResourceNodeRepo nodes;
  private final SiegeRepo sieges;
  private final MovementRepo movements;
  private final TravelTimeService travel;
  private final NodeService nodeService;
  private final SiegeService siegeService;

  public TroopDetailService(CityRepo cities, PlayerRepo players, AllianceRepo alliances,
                            ReinforcementRepo reinforcements, ResourceNodeRepo nodes, SiegeRepo sieges,
                            MovementRepo movements, TravelTimeService travel,
                            NodeService nodeService, SiegeService siegeService){
    this.cities=cities; this.players=players; this.alliances=alliances; this.reinforcements=reinforcements;
    this.nodes=nodes; this.sieges=sieges; this.movements=movements; this.travel=travel;
    this.nodeService=nodeService; this.siegeService=siegeService;
  }

  private City ownCity(Long playerId, Long cityId){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    return c;
  }
  private static boolean any(Map<String,Integer> u){ if (u==null) return false; for (int v: u.values()) if (v>0) return true; return false; }
  private String cityName(Long id){ return id==null?null:cities.findById(id).map(City::getName).orElse("Unknown city"); }

  // ---- TAB 1: my troops abroad -------------------------------------------------------------------
  @Transactional(readOnly = true)
  public List<Map<String,Object>> troopsAbroad(Long playerId, Long cityId){
    ownCity(playerId, cityId);
    List<Map<String,Object>> out = new ArrayList<>();

    // support/reinforcements I own, stationed at OTHER cities (not this one) — aggregate per host
    // (a host may hold troops I sent from several of my cities; recall returns each to its origin)
    Map<Long,Map<String,Integer>> byHost = new LinkedHashMap<>();
    for (Reinforcement r : reinforcements.findByOwnerPlayerId(playerId)){
      if (Objects.equals(r.getHostCityId(), cityId) || !any(r.getUnits())) continue;
      Map<String,Integer> agg = byHost.computeIfAbsent(r.getHostCityId(), k -> new LinkedHashMap<>());
      r.getUnits().forEach((u,n) -> { if (n!=null && n>0) agg.merge(u, n, Integer::sum); });
    }
    for (var e : byHost.entrySet()){
      Map<String,Object> row = new LinkedHashMap<>();
      row.put("locationType", "ALLY_CITY");
      row.put("locationId", e.getKey());
      row.put("locationName", cityName(e.getKey()));
      row.put("troops", e.getValue());
      out.add(row);
    }
    // resource buildings I control — my own stack (per-player garrison)
    for (ResourceNode n : nodes.findByControllingPlayerId(playerId)){
      Map<String,Integer> myStack = n.getGarrison().get(String.valueOf(playerId));
      if (!any(myStack)) continue;
      Map<String,Object> row = new LinkedHashMap<>();
      row.put("locationType", "NODE");
      row.put("locationId", n.getId());
      row.put("locationName", titleCase(n.getNodeType().name()) + " (Lv " + n.getLevel() + ")");
      row.put("troops", new LinkedHashMap<>(myStack));
      out.add(row);
    }
    // sieges I am running (the besieging force is locked away)
    for (SiegeStatus st : List.of(SiegeStatus.ACTIVE, SiegeStatus.BROKEN))
      for (Siege s : sieges.findByBesiegingPlayerIdAndStatus(playerId, st)){
        Map<String,Integer> force = new LinkedHashMap<>();
        if (s.getBesiegingTroops()!=null) s.getBesiegingTroops().forEach((k,v)->force.merge(k,v,Integer::sum));
        if (s.getBesiegingShips()!=null)  s.getBesiegingShips().forEach((k,v)->force.merge(k,v,Integer::sum));
        if (!any(force)) continue;
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("locationType", "SIEGE");
        row.put("locationId", s.getId());
        row.put("locationName", "Siege of " + cityName(s.getCityId()));
        row.put("troops", force);
        out.add(row);
      }
    return out;
  }

  // ---- TAB 2: foreign troops in this city --------------------------------------------------------
  @Transactional(readOnly = true)
  public List<Map<String,Object>> foreignTroops(Long playerId, Long cityId){
    ownCity(playerId, cityId);
    List<Map<String,Object>> out = new ArrayList<>();
    for (Reinforcement r : reinforcements.findByHostCityId(cityId)){
      if (Objects.equals(r.getOwnerPlayerId(), playerId) || !any(r.getUnits())) continue;
      Player owner = players.findById(r.getOwnerPlayerId()).orElse(null);
      Map<String,Object> row = new LinkedHashMap<>();
      row.put("ownerPlayerId", r.getOwnerPlayerId());
      row.put("ownerName", owner!=null ? owner.getUsername() : "Unknown");
      row.put("ownerAlliance", owner!=null && owner.getAllianceId()!=null
          ? alliances.findById(owner.getAllianceId()).map(Alliance::getName).orElse(null) : null);
      row.put("troops", new LinkedHashMap<>(r.getUnits()));
      out.add(row);
    }
    return out;
  }

  // ---- recall my troops home ---------------------------------------------------------------------
  @Transactional
  public void recallAbroad(Long playerId, Long cityId, String locationType, Long locationId){
    City home = ownCity(playerId, cityId);
    switch (locationType==null ? "" : locationType){
      case "ALLY_CITY" -> {
        // recall ALL of my reinforcement stacks at that host — each marches back to ITS origin city
        List<Reinforcement> mine = reinforcements.findByHostCityId(locationId).stream()
            .filter(r -> Objects.equals(r.getOwnerPlayerId(), playerId) && any(r.getUnits())).toList();
        if (mine.isEmpty()) throw new IllegalArgumentException("No troops of yours at that city");
        for (Reinforcement r : mine){
          Map<String,Integer> units = new LinkedHashMap<>(r.getUnits());
          City dest = originHome(r.getOriginCityId(), playerId, home);
          reinforcements.delete(r);
          sendReturn(dest, locationId, units, playerId);
        }
      }
      case "NODE"  -> nodeService.withdraw(playerId, locationId, null);   // returns to the claiming city; may abandon node
      case "SIEGE" -> siegeService.withdraw(playerId, locationId);        // pulls the besieging force home
      default -> throw new IllegalArgumentException("Unknown location type: " + locationType);
    }
  }

  // ---- city owner ejects foreign troops back to their owner --------------------------------------
  @Transactional
  public void dismissForeign(Long playerId, Long cityId, Long ownerPlayerId){
    ownCity(playerId, cityId);
    if (Objects.equals(ownerPlayerId, playerId)) throw new IllegalStateException("Those are your own troops");
    List<Reinforcement> rows = reinforcements.findByHostCityId(cityId).stream()
        .filter(r -> Objects.equals(r.getOwnerPlayerId(), ownerPlayerId) && any(r.getUnits())).toList();
    if (rows.isEmpty()) throw new IllegalArgumentException("No such foreign troops in this city");
    for (Reinforcement r : rows){
      Map<String,Integer> units = new LinkedHashMap<>(r.getUnits());
      City dest = originHome(r.getOriginCityId(), ownerPlayerId, null);
      reinforcements.delete(r);
      if (dest != null) sendReturn(dest, cityId, units, ownerPlayerId);   // dest null → owner has no city; disband
    }
  }

  /** The city troops return to: their recorded origin if it still belongs to the owner, else the
   *  owner's capital (legacy rows). {@code fallback} is used when no capital can be found. */
  private City originHome(Long originCityId, Long ownerPlayerId, City fallback){
    if (originCityId != null){
      City origin = cities.findById(originCityId).orElse(null);
      if (origin != null && Objects.equals(origin.getPlayerId(), ownerPlayerId)) return origin;
    }
    return cities.findByPlayerIdAndCapitalTrue(ownerPlayerId)
        .orElseGet(() -> cities.findByPlayerId(ownerPlayerId).stream().findFirst().orElse(fallback));
  }

  /** RETURN movement: troops leave {@code fromCityId}, deposit at {@code home} (the source) on arrival. */
  private void sendReturn(City home, Long fromCityId, Map<String,Integer> units, Long ownerPlayerId){
    if (!any(units)) return;
    City from = cities.findById(fromCityId).orElse(null);
    Long fromIsland = from!=null ? from.getIslandId() : home.getIslandId();
    Movement m = new Movement();
    m.setWorldId(home.getWorldId());
    m.setPlayerId(ownerPlayerId);
    m.setSourceCityId(home.getId());     // deposit target on arrival
    m.setTargetCityId(fromCityId);       // the place they're leaving (line shows from→home)
    m.setPhase(MovementPhase.RETURN);
    m.setUnits(new LinkedHashMap<>(units));
    m.setArriveAt(Instant.now().plusSeconds(
        travel.seconds(fromIsland, home.getIslandId(), travel.slowestMinutesPerTile(units))));
    movements.save(m);
  }

  private static String titleCase(String s){
    if (s==null || s.isEmpty()) return s;
    return s.charAt(0) + s.substring(1).toLowerCase().replace('_',' ');
  }
}
