package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * City founding via the account hero (PART: Founding Cities & Race Selection).
 *
 * <p>Flow: the player sends their hero to an empty island slot (a {@link MovementPhase#SETTLE}
 * movement). On arrival the movement parks — {@code arrivedAt} is set but it is NOT resolved —
 * and the hero waits ({@link HeroState#SETTLING}) until the player picks one of the four
 * {@link Race}s. Founding then creates the city, applies the race, and frees the hero.
 *
 * <p>Existing expansion gates are reused: the per-level city cap ({@link GameRules#citySlots}),
 * and the single-hero rule (the hero must be IDLE and stationed in the origin city to march).
 */
@Service
public class SettleService {
  /** A founding left un-chosen for this long is abandoned: the hero marches home automatically. */
  static final long ABANDON_AFTER_SECONDS = 24 * 3600;

  private final CityRepo cities;
  private final PlayerRepo players;
  private final IslandRepo islands;
  private final AllianceRepo alliances;
  private final HeroService heroes;
  private final HeroRepo heroRepo;
  private final MovementRepo movements;
  private final TravelTimeService travel;
  private final CityFactory cityFactory;
  private final MissionService missions;

  public SettleService(CityRepo cities, PlayerRepo players, IslandRepo islands, AllianceRepo alliances,
                       HeroService heroes, HeroRepo heroRepo, MovementRepo movements,
                       TravelTimeService travel, CityFactory cityFactory, MissionService missions){
    this.cities=cities; this.players=players; this.islands=islands; this.alliances=alliances;
    this.heroes=heroes; this.heroRepo=heroRepo; this.movements=movements; this.travel=travel;
    this.cityFactory=cityFactory; this.missions=missions;
  }

  // --- dispatch: send the hero to found a city -------------------------------

  @Transactional
  public Movement settle(Long playerId, Long islandId, int slotIndex, Long fromCityId, Long heroId){
    Player p = players.findById(playerId).orElseThrow();
    if (slotIndex < 0 || slotIndex >= GameRules.SLOTS_PER_ISLAND)
      throw new IllegalArgumentException("Invalid slot");
    islands.findById(islandId).orElseThrow(() -> new IllegalArgumentException("Island not found"));
    if (cities.findByIslandIdAndSlot(islandId, slotIndex).isPresent())
      throw new IllegalStateException("That slot is already occupied");

    City from = cities.findById(fromCityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(from.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");

    if (heroId == null) throw new IllegalArgumentException("Choose a hero to send");
    Hero h = heroes.requireOwned(playerId, heroId);
    if (!h.isUnlocked()) throw new IllegalStateException("That hero is not unlocked yet");
    if (h.getState() != HeroState.IDLE) throw new IllegalStateException("That hero is not available to settle");
    if (!Objects.equals(h.getStationedCityId(), fromCityId))
      throw new IllegalStateException("That hero is not stationed in that city");

    long owned = cities.countByPlayerId(playerId);
    int pending = movements.findByPlayerIdAndPhaseAndResolvedFalse(playerId, MovementPhase.SETTLE).size();
    // max cities = player level (cap 20). Reach the next level by earning Culture at a Temple.
    if (owned + pending >= GameRules.maxCities(p.getLevel()))
      throw new IllegalStateException("All city slots used — earn Culture at a Temple to reach the next level");

    long secs = travel.seconds(from.getIslandId(), islandId, TravelTimeService.DEFAULT_MINUTES_PER_TILE);
    if (from.getRace() != null) secs = (long)(secs * from.getRace().travelMult);
    secs = Math.max(5, (long)(secs * heroes.travelMult(h)));

    Movement m = new Movement();
    m.setWorldId(from.getWorldId()); m.setPlayerId(playerId); m.setSourceCityId(fromCityId);
    m.setTargetIslandId(islandId); m.setTargetSlot(slotIndex); m.setPhase(MovementPhase.SETTLE);
    m.setArriveAt(Instant.now().plusSeconds(secs));
    Movement saved = movements.save(m);

    heroes.sendHero(playerId, heroId, fromCityId, saved.getId());  // hero -> MARCHING
    return saved;
  }

  // --- arrival (called from the tick) ---------------------------------------

  /**
   * The hero reaches the slot. If the slot was taken while marching, abort: resolve the movement
   * and send the hero straight home. Otherwise park the movement and put the hero into the
   * SETTLING wait until the player picks a race.
   */
  @Transactional
  public void onArrive(Movement m, Instant now){
    if (m.getArrivedAt() != null) return;
    Hero h = heroRepo.findByActiveMovementId(m.getId()).orElse(null);

    boolean slotTaken = m.getTargetSlot() != null
        && cities.findByIslandIdAndSlot(m.getTargetIslandId(), m.getTargetSlot()).isPresent();
    if (slotTaken){
      m.setArrivedAt(now); m.setResolved(true); movements.save(m);
      if (h != null) marchHeroHome(h, m.getSourceCityId(), m.getTargetIslandId());  // turn around, march home
      return;
    }

    m.setArrivedAt(now); movements.save(m);
    if (h != null){ h.setState(HeroState.SETTLING); heroRepo.save(h); }
  }

  /** Foundings the player never completed: send the hero home and release the slot. */
  @Transactional
  public void releaseAbandoned(Instant now){
    for (Movement m : movements.findByPhaseAndResolvedFalse(MovementPhase.SETTLE)){
      if (m.getArrivedAt() == null) continue;
      if (m.getArrivedAt().plusSeconds(ABANDON_AFTER_SECONDS).isAfter(now)) continue;
      heroRepo.findByActiveMovementId(m.getId()).ifPresent(h -> marchHeroHome(h, m.getSourceCityId(), m.getTargetIslandId()));
      m.setResolved(true); movements.save(m);
    }
  }

  // --- found the city (race choice) -----------------------------------------

  @Transactional
  public City foundCity(Long playerId, Long islandId, int slotIndex, String raceName,
                        String cityName, Long heroReturnCityId){
    Movement m = movements.findByPlayerIdAndPhaseAndResolvedFalse(playerId, MovementPhase.SETTLE).stream()
        .filter(x -> x.getArrivedAt()!=null && Objects.equals(x.getTargetIslandId(), islandId)
            && x.getTargetSlot()!=null && x.getTargetSlot()==slotIndex)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No founding is awaiting a race choice on that slot"));

    Race race;
    try { race = Race.valueOf(raceName); }
    catch (Exception e){ throw new IllegalArgumentException("Pick a race: HUMANS, GIANTS, FAIRIES or NEWTS"); }

    Player p = players.findById(playerId).orElseThrow();
    // the hero that actually settled this slot (could be Leo or Titania)
    Hero h = heroRepo.findByActiveMovementId(m.getId())
        .orElseThrow(() -> new IllegalStateException("Settling hero not found"));

    // race for the slot while the hero dithered? abort and send the hero home.
    // Return null (do NOT throw) so these writes commit — a thrown exception would roll the
    // hero's homeward march and the movement-resolve back, leaving the hero stuck at the slot.
    if (cities.findByIslandIdAndSlot(islandId, slotIndex).isPresent()){
      m.setResolved(true); movements.save(m);
      marchHeroHome(h, m.getSourceCityId(), islandId);
      return null;
    }

    String name = (cityName==null || cityName.isBlank())
        ? p.getUsername() + " Colony"
        : (cityName.length()>40 ? cityName.substring(0,40) : cityName.trim());
    City city = cityFactory.createPlayerCity(p.getWorldId(), playerId, islandId, slotIndex, name, false, race);

    m.setResolved(true); movements.save(m);
    missions.record(playerId, MissionObjectiveType.FOUND_CITY, 1);

    if (heroReturnCityId == null || heroReturnCityId.equals(city.getId())){
      // station the hero in the freshly founded city
      h.setState(HeroState.IDLE); h.setStationedCityId(city.getId()); h.setActiveMovementId(null);
      heroRepo.save(h);
    } else {
      City ret = cities.findById(heroReturnCityId).orElseThrow(() -> new IllegalArgumentException("Return city not found"));
      if (!Objects.equals(ret.getPlayerId(), playerId)) throw new IllegalStateException("Return city is not yours");
      marchHeroHome(h, heroReturnCityId, islandId);
    }
    return city;
  }

  /** Dispatch a RETURN movement carrying just the hero from {@code fromIslandId} to a home city. */
  private void marchHeroHome(Hero h, Long returnCityId, Long fromIslandId){
    City ret = cities.findById(returnCityId).orElse(null);
    if (ret == null){ h.setState(HeroState.IDLE); h.setActiveMovementId(null); heroRepo.save(h); return; }
    long secs = travel.seconds(fromIslandId, ret.getIslandId(), TravelTimeService.DEFAULT_MINUTES_PER_TILE);
    secs = Math.max(5, (long)(secs * heroes.travelMult(h)));
    Movement r = new Movement();
    r.setWorldId(ret.getWorldId()); r.setPlayerId(h.getOwnerPlayerId()); r.setSourceCityId(returnCityId);
    r.setPhase(MovementPhase.RETURN); r.setArriveAt(Instant.now().plusSeconds(secs));
    Movement saved = movements.save(r);
    h.setState(HeroState.MARCHING); h.setActiveMovementId(saved.getId()); h.setStationedCityId(null);
    heroRepo.save(h);
  }

  // --- read views -----------------------------------------------------------

  /** All 12 slots of an island with occupancy, owner/race for the player's map view. */
  @Transactional(readOnly = true)
  public Map<String,Object> slots(Long playerId, Long islandId){
    Player me = players.findById(playerId).orElseThrow();
    Island isl = islands.findById(islandId).orElseThrow(() -> new IllegalArgumentException("Island not found"));
    // any unlocked, idle hero can be sent to settle
    boolean heroAvail = heroes.list(playerId).stream()
        .anyMatch(x -> x.isUnlocked() && x.getState() == HeroState.IDLE);

    long owned = cities.countByPlayerId(playerId);
    int pending = movements.findByPlayerIdAndPhaseAndResolvedFalse(playerId, MovementPhase.SETTLE).size();
    boolean capReached = owned + pending >= GameRules.citySlots(me.getLevel());
    String blockReason = capReached ? "City limit reached — level up to settle more"
        : !heroAvail ? "No idle hero available to settle" : null;

    Map<Integer,City> occupied = new HashMap<>();
    for (City c : cities.findByIslandId(islandId)) occupied.put(c.getSlot(), c);

    List<Map<String,Object>> slots = new ArrayList<>();
    for (int i=0; i<GameRules.SLOTS_PER_ISLAND; i++){
      Map<String,Object> s = new LinkedHashMap<>();
      s.put("slotIndex", i);
      City c = occupied.get(i);
      if (c != null){
        s.put("status", "OCCUPIED");
        s.put("cityId", c.getId());
        s.put("cityName", c.getName());
        s.put("points", c.getPoints());
        Long pid = c.getPlayerId();
        s.put("ownerName", pid==null ? "Barbarians"
            : players.findById(pid).map(Player::getUsername).orElse("Unknown"));
        s.put("faction", faction(me, pid));
        s.put("alliance", allianceName(pid));
        s.put("race", c.getRace()==null ? null : c.getRace().dto());
      } else {
        s.put("status", "EMPTY");
        s.put("canSettle", !capReached && heroAvail);
        s.put("reason", (!capReached && heroAvail) ? null : blockReason);
      }
      slots.add(s);
    }

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("islandId", islandId);
    out.put("islandName", isl.getName());
    out.put("slotsPerIsland", GameRules.SLOTS_PER_ISLAND);
    out.put("occupied", occupied.size());
    out.put("slots", slots);
    return out;
  }

  /** Travel-time preview for sending a hero from {@code fromCityId} to an island. */
  @Transactional(readOnly = true)
  public Map<String,Object> settlePreview(Long playerId, Long islandId, Long fromCityId, Long heroId){
    City from = cities.findById(fromCityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(from.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    islands.findById(islandId).orElseThrow(() -> new IllegalArgumentException("Island not found"));
    long secs = travel.seconds(from.getIslandId(), islandId, TravelTimeService.DEFAULT_MINUTES_PER_TILE);
    if (from.getRace() != null) secs = (long)(secs * from.getRace().travelMult);
    if (heroId != null) secs = (long)(secs * heroes.travelMult(heroes.requireOwned(playerId, heroId)));
    secs = Math.max(5, secs);
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("travelSeconds", secs);
    out.put("distance", Math.round(travel.distanceTiles(from.getIslandId(), islandId) * 10.0) / 10.0);
    out.put("slowestUnit", null);
    out.put("arriveAt", Instant.now().plusSeconds(secs).toString());
    return out;
  }

  /** The player's in-progress settle / pending founding, or {@code {founding:null}}. */
  @Transactional(readOnly = true)
  public Map<String,Object> foundingStatus(Long playerId){
    List<Movement> pend = movements.findByPlayerIdAndPhaseAndResolvedFalse(playerId, MovementPhase.SETTLE);
    Map<String,Object> out = new LinkedHashMap<>();
    if (pend.isEmpty()){ out.put("founding", null); return out; }
    // Prefer a founding that has ARRIVED and awaits a race choice. With more than one settle pending,
    // returning an arbitrary first (a still-marching one) made the race banner target the wrong slot —
    // the choice would then resolve against a different slot than the player saw.
    Movement m = pend.stream().filter(x -> x.getArrivedAt()!=null).findFirst().orElse(pend.get(0));
    Map<String,Object> f = new LinkedHashMap<>();
    f.put("movementId", m.getId());
    f.put("phase", m.getArrivedAt()!=null ? "AWAITING_RACE" : "MARCHING");
    f.put("islandId", m.getTargetIslandId());
    f.put("islandName", islands.findById(m.getTargetIslandId()).map(Island::getName).orElse("?"));
    f.put("slotIndex", m.getTargetSlot());
    f.put("fromCityId", m.getSourceCityId());
    f.put("arriveAt", m.getArriveAt()==null ? null : m.getArriveAt().toString());
    f.put("arrivedAt", m.getArrivedAt()==null ? null : m.getArrivedAt().toString());
    out.put("founding", f);
    return out;
  }

  private String faction(Player me, Long ownerId){
    if (ownerId == null) return "barbarian";
    if (Objects.equals(ownerId, me.getId())) return "self";
    Long mine = me.getAllianceId();
    Long theirs = players.findById(ownerId).map(Player::getAllianceId).orElse(null);
    return mine != null && mine.equals(theirs) ? "ally" : "enemy";
  }
  private String allianceName(Long ownerId){
    if (ownerId == null) return null;
    Long aid = players.findById(ownerId).map(Player::getAllianceId).orElse(null);
    return aid == null ? null : alliances.findById(aid).map(Alliance::getName).orElse(null);
  }
}
