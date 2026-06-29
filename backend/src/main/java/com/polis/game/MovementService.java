package com.polis.game;

import com.polis.api.MovementDTO;
import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Read-side service for the Troop Movements feature: assembles per-city and
 * per-player movement views and the live attack preview. Battle resolution and
 * troop deduction stay in {@link BuildService}/{@link TickScheduler}.
 */
@Service
public class MovementService {
  private final MovementRepo movements;
  private final CityRepo cities;
  private final PlayerRepo players;
  private final IslandRepo islands;
  private final TravelTimeService travel;
  private final HeroRepo heroes;
  private final TradeConvoyRepo tradeConvoys;

  public MovementService(MovementRepo movements, CityRepo cities, PlayerRepo players,
                         IslandRepo islands, TravelTimeService travel, HeroRepo heroes,
                         TradeConvoyRepo tradeConvoys){
    this.movements = movements; this.cities = cities; this.players = players;
    this.islands = islands; this.travel = travel; this.heroes = heroes; this.tradeConvoys = tradeConvoys;
  }

  /** Preview the travel time for an attack without creating a movement. */
  @Transactional(readOnly = true)
  public Map<String,Object> preview(Long playerId, Long originCityId, Long targetCityId, Map<String,Integer> units, Long heroId){
    City origin = cities.findById(originCityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(origin.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    City target = cities.findById(targetCityId).orElseThrow(() -> new IllegalArgumentException("Target not found"));
    Duration d = travel.travelTime(originCityId, targetCityId, units);
    int heroLoad = heroId == null ? 0 : heroes.findById(heroId)
        .filter(h -> Objects.equals(h.getOwnerPlayerId(), playerId))
        .map(h -> travel.heroLandLoad(h.getRace())).orElse(0);
    var tc = travel.checkTransport(units, travel.crossesWater(origin.getIslandId(), target.getIslandId()), heroLoad);
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("travelSeconds", d.getSeconds());
    out.put("distance", Math.round(travel.distanceTiles(origin.getIslandId(), target.getIslandId()) * 10.0) / 10.0);
    out.put("slowestUnit", travel.slowestUnit(units));
    out.put("arriveAt", Instant.now().plus(d).toString());
    // movement-class / transport summary for the dispatch UI
    out.put("routeCrossesWater", tc.crossesWater());
    out.put("requiredTransportCapacity", tc.requiredCapacity());
    out.put("providedTransportCapacity", tc.providedCapacity());
    out.put("transportSufficient", tc.sufficient());
    out.put("transportShipsShort", tc.shipsShort());
    if (tc.reason()!=null) out.put("transportWarning", tc.reason());
    return out;
  }

  /** All unresolved movements involving a city the player owns: outgoing, returning, and incoming hostile. */
  @Transactional(readOnly = true)
  public List<MovementDTO> cityMovements(Long playerId, Long cityId){
    City city = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(city.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");

    Map<Long,String> nameCache = new HashMap<>();
    Map<Long,String> islNameCache = new HashMap<>();
    Map<Long,String> ownerCache = new HashMap<>();
    LinkedHashMap<Long,MovementDTO> out = new LinkedHashMap<>();

    // outgoing attacks / colonies, plus armies returning home to this city
    for (Movement m : movements.findBySourceCityIdAndResolvedFalse(cityId))
      out.put(m.getId(), toDto(m, playerId, nameCache, islNameCache, ownerCache));
    // hostile armies inbound to this city
    for (Movement m : movements.findByTargetCityIdAndResolvedFalse(cityId))
      if (m.getPhase() == MovementPhase.OUT && !Objects.equals(m.getPlayerId(), playerId))
        out.put(m.getId(), toDto(m, playerId, nameCache, islNameCache, ownerCache));

    // trade convoys touching this city (leaving from it or delivering to it)
    for (TradeConvoy cv : tradeConvoys.findByBuyerPlayerIdAndStatusIn(playerId,
        List.of(ConvoyStatus.PENDING, ConvoyStatus.IN_TRANSIT)))
      if (Objects.equals(cv.getOriginCityId(), cityId) || Objects.equals(cv.getDestinationCityId(), cityId))
        out.put(-cv.getId(), convoyDto(cv, nameCache));

    return new ArrayList<>(out.values());
  }

  /** Every movement across all of the player's cities, plus incoming hostile, with a summary. */
  @Transactional(readOnly = true)
  public Map<String,Object> playerMovements(Long playerId){
    Map<Long,String> nameCache = new HashMap<>();
    Map<Long,String> islNameCache = new HashMap<>();
    Map<Long,String> ownerCache = new HashMap<>();

    List<City> myCities = cities.findByPlayerId(playerId);
    Set<Long> myCityIds = new HashSet<>();
    for (City c : myCities){ myCityIds.add(c.getId()); nameCache.put(c.getId(), c.getName()); }

    LinkedHashMap<Long,MovementDTO> dtos = new LinkedHashMap<>();
    for (Movement m : movements.findByPlayerIdAndResolvedFalse(playerId))
      dtos.put(m.getId(), toDto(m, playerId, nameCache, islNameCache, ownerCache));
    if (!myCityIds.isEmpty())
      for (Movement m : movements.findByTargetCityIdInAndResolvedFalse(myCityIds))
        if (m.getPhase() == MovementPhase.OUT && !Objects.equals(m.getPlayerId(), playerId))
          dtos.put(m.getId(), toDto(m, playerId, nameCache, islNameCache, ownerCache));

    // the player's in-flight + queued trade convoys
    for (TradeConvoy cv : tradeConvoys.findByBuyerPlayerIdAndStatusIn(playerId,
        List.of(ConvoyStatus.PENDING, ConvoyStatus.IN_TRANSIT)))
      dtos.put(-cv.getId(), convoyDto(cv, nameCache));

    List<MovementDTO> list = new ArrayList<>(dtos.values());

    int attacksOut = 0, returning = 0, incomingThreats = 0;
    Set<Long> busyCities = new HashSet<>();
    for (MovementDTO d : list){
      if (d.hostile()) incomingThreats++;
      else if ("RETURN".equals(d.type())) { returning++; busyCities.add(d.targetCityId()); }
      else if ("ATTACK".equals(d.type()) || "COLONY".equals(d.type())) { attacksOut++; busyCities.add(d.originCityId()); }
    }
    int idleCities = (int) myCities.stream().filter(c -> !busyCities.contains(c.getId())).count();

    Map<String,Object> summary = new LinkedHashMap<>();
    summary.put("attacksOut", attacksOut);
    summary.put("incomingThreats", incomingThreats);
    summary.put("returning", returning);
    summary.put("idleCities", idleCities);

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("summary", summary);
    out.put("movements", list);
    return out;
  }

  // --- helpers -------------------------------------------------------------

  /** Public so the attack endpoint can return the freshly created movement to the caller. */
  @Transactional(readOnly = true)
  public MovementDTO dto(Movement m, Long viewerId){
    return toDto(m, viewerId, new HashMap<>(), new HashMap<>(), new HashMap<>());
  }

  private MovementDTO toDto(Movement m, Long viewerId,
                            Map<Long,String> nameCache, Map<Long,String> islNameCache, Map<Long,String> ownerCache){
    boolean mine = Objects.equals(m.getPlayerId(), viewerId);
    String type, status;
    switch (m.getPhase()){
      case RETURN -> { type = "RETURN"; status = "RETURNING"; }
      case COLONY -> { type = "COLONY"; status = "TRAVELLING"; }
      case SETTLE -> { type = "SETTLE"; status = m.getArrivedAt()!=null ? "SETTLING" : "TRAVELLING"; }
      default     -> { type = "ATTACK"; status = "TRAVELLING"; }
    }

    Long originId, targetId; String originName, targetName;
    if (m.getTargetCampId() != null){
      // bandit-camp raid: outbound marches to the camp, the return brings the army home
      if (m.getPhase() == MovementPhase.RETURN){
        originId = null; originName = "🏴‍☠️ Bandit Camp";
        targetId = m.getSourceCityId(); targetName = cityName(targetId, nameCache);
      } else {
        originId = m.getSourceCityId(); originName = cityName(originId, nameCache);
        targetId = null; targetName = "🏴‍☠️ Bandit Camp";
      }
    } else if (m.getPhase() == MovementPhase.RETURN){
      // a returning army leaves the raided target and marches back to its home (source) city
      originId = m.getTargetCityId(); originName = cityName(originId, nameCache);
      targetId = m.getSourceCityId(); targetName = cityName(targetId, nameCache);
    } else if (m.getPhase() == MovementPhase.COLONY || m.getPhase() == MovementPhase.SETTLE){
      originId = m.getSourceCityId(); originName = cityName(originId, nameCache);
      targetId = null;
      targetName = islandName(m.getTargetIslandId(), islNameCache)
          + (m.getTargetSlot() != null ? " · plot " + (m.getTargetSlot() + 1) : "");
    } else {
      originId = m.getSourceCityId(); originName = cityName(originId, nameCache);
      targetId = m.getTargetCityId(); targetName = cityName(targetId, nameCache);
    }

    boolean hostile = !mine && m.getPhase() == MovementPhase.OUT;
    boolean unitsKnown = mine; // no spy reports yet → enemy composition stays hidden
    Map<String,Integer> units = unitsKnown ? m.getUnits() : null;
    Map<String,Long> loot = (mine && m.getPhase() == MovementPhase.RETURN) ? m.getLoot() : null;

    return new MovementDTO(
        m.getId(), type, status,
        originId, originName, targetId, targetName,
        ownerName(m.getPlayerId(), ownerCache), mine, hostile, unitsKnown,
        units, loot,
        m.getDepartAt() == null ? null : m.getDepartAt().toString(),
        m.getArriveAt() == null ? null : m.getArriveAt().toString());
  }

  /** Trade convoy as a MovementDTO (negative id to stay distinct from troop-movement ids). */
  private MovementDTO convoyDto(TradeConvoy cv, Map<Long,String> nameCache){
    String status = cv.getStatus() == ConvoyStatus.PENDING ? "PENDING" : "TRAVELLING";
    return new MovementDTO(
        -cv.getId(), "TRADE", status,
        cv.getOriginCityId(), cityName(cv.getOriginCityId(), nameCache),
        cv.getDestinationCityId(), cityName(cv.getDestinationCityId(), nameCache),
        null, true, false, true,
        null, cv.getCargo(),
        cv.getDepartAt() == null ? null : cv.getDepartAt().toString(),
        cv.getArriveAt() == null ? null : cv.getArriveAt().toString());
  }

  private String cityName(Long id, Map<Long,String> cache){
    if (id == null) return "?";
    return cache.computeIfAbsent(id, k -> cities.findById(k).map(City::getName).orElse("Unknown city"));
  }
  private String islandName(Long id, Map<Long,String> cache){
    if (id == null) return "?";
    return cache.computeIfAbsent(id, k -> islands.findById(k).map(Island::getName).orElse("Unknown island"));
  }
  private String ownerName(Long playerId, Map<Long,String> cache){
    if (playerId == null) return "Barbarians";
    return cache.computeIfAbsent(playerId, k -> players.findById(k).map(Player::getUsername).orElse("Unknown"));
  }
}
