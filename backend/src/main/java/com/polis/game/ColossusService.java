package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * COLOSSI — daily roaming PvE world bosses (sea monsters).
 *
 * <p>One Colossus spawns each day at 21:00, sails an arc of the Tier 2 / Tier 3 boundary ring, and
 * despawns at 22:00. It has a large shared health pool that never regenerates and a daily-varying
 * elemental defence profile (so the strongest attacking element rotates day to day). Any alliance's
 * players attack it with sea-layer fleets or flying units; each resolved attack subtracts damage from
 * the pool and adds it to the attacker's alliance tally. On defeat the reward pool is split strictly
 * by each alliance's share of total damage and paid into alliance treasuries — no last-hit bonus.
 *
 * <p>Reuses the bandit-camp roaming-attack flow (dispatch an OUT movement, resolve on arrival, march
 * survivors home) and the island-boss combat/elemental/item-drop model.
 */
@Service
public class ColossusService {
  private final ColossusRepo colossi;
  private final ColossusDamageRepo damages;
  private final AllianceRepo alliances;
  private final PlayerRepo players;
  private final CityRepo cities;
  private final IslandRepo islands;
  private final UnitRepo units;
  private final UnitCatalog catalog;
  private final CombatEngine combat;
  private final HeroItemRepo heroItems;
  private final ItemFactory itemFactory;
  private final BattleReportService reports;
  private final LibraryService library;
  private final MovementRepo movements;
  private final WorldRepo worlds;
  private final TravelTimeService travel;
  private final Random rnd = new Random();

  @Value("${polis.colossus.base-health:4000000}") private long baseHealth;
  @Value("${polis.colossus.tier:3}")              private int dailyTier;

  public ColossusService(ColossusRepo colossi, ColossusDamageRepo damages, AllianceRepo alliances,
                         PlayerRepo players, CityRepo cities, IslandRepo islands, UnitRepo units,
                         UnitCatalog catalog, CombatEngine combat,
                         HeroItemRepo heroItems, ItemFactory itemFactory, BattleReportService reports,
                         LibraryService library, MovementRepo movements, WorldRepo worlds, TravelTimeService travel){
    this.colossi=colossi; this.damages=damages; this.alliances=alliances; this.players=players;
    this.cities=cities; this.islands=islands; this.units=units; this.catalog=catalog; this.combat=combat;
    this.heroItems=heroItems; this.itemFactory=itemFactory; this.reports=reports;
    this.library=library; this.movements=movements; this.worlds=worlds; this.travel=travel;
  }

  // ---- naming + elemental profiles -------------------------------------------------------------

  private static final String[] NAMES = { "Tidewrath", "Maelstrom", "Brinecolossus", "Abyssal Wyrm", "Stormjaw", "Deepfury" };
  private static final Element[] ELEMS = Element.values();

  /** Total defensive bulk shared across the four elements — constant day to day (only distribution rotates). */
  private static final int DEFENSE_BULK = 400;

  // ---- daily spawn / despawn -------------------------------------------------------------------

  /**
   * Spawn today's Colossus on the Tier 2/3 ring with a freshly-rolled elemental profile. Idempotent
   * for the day: if one is already ROAMING in the world, does nothing. Called by the 21:00 scheduler.
   */
  @Transactional
  public void spawnDaily(Instant now){
    for (World w : worlds.findAll()){
      if (!colossi.findByWorldIdAndStatus(w.getId(), ColossusStatus.ROAMING).isEmpty()) continue;
      Colossus c = new Colossus();
      c.setWorldId(w.getId());
      c.setTier(dailyTier);
      c.setName(NAMES[rnd.nextInt(NAMES.length)]);
      long hp = baseHealth * dailyTier;
      c.setMaxHealth(hp); c.setCurrentHealth(hp);
      c.setStatus(ColossusStatus.ROAMING);
      // route: an arc of the T2/T3 boundary ring, start angle rotated daily so regions take turns
      c.setCenterX(GameRules.WORLD_CENTER_X); c.setCenterY(GameRules.WORLD_CENTER_Y);
      c.setRadius(GameRules.COLOSSUS_RING_RADIUS);
      c.setStartAngle(rnd.nextDouble() * 2 * Math.PI);
      c.setArcSpan(Math.PI / 2);                         // sweeps a quarter of the ring over the hour
      rollElementalProfile(c);
      c.setSpawnedAt(now);
      c.setDespawnAt(now.plusSeconds(3600));             // exactly one hour
      colossi.save(c);
    }
  }

  /** Re-roll the four elemental defences: one weak element, one resisted, two medium — bulk constant. */
  private void rollElementalProfile(Colossus c){
    List<Element> order = new ArrayList<>(Arrays.asList(ELEMS));
    Collections.shuffle(order, rnd);
    Element weak = order.get(0), strong = order.get(1);
    // weak ~15%, strong ~40%, the rest split the remaining 45% — sums to DEFENSE_BULK
    EnumMap<Element,Integer> def = new EnumMap<>(Element.class);
    def.put(weak,   Math.round(DEFENSE_BULK * 0.15f));
    def.put(strong, Math.round(DEFENSE_BULK * 0.40f));
    int rest = DEFENSE_BULK - def.get(weak) - def.get(strong);
    def.put(order.get(2), rest / 2);
    def.put(order.get(3), rest - rest / 2);
    c.setDefenseFire(def.get(Element.FIRE));   c.setDefenseWind(def.get(Element.WIND));
    c.setDefenseEarth(def.get(Element.EARTH)); c.setDefenseWater(def.get(Element.WATER));
    c.setAttackElement(weak);                  // it strikes with the element it least defends (flavour)
  }

  /** Despawn any Colossus whose hour is up: DEFEATED already paid out at the killing tick; survivors time out. */
  @Transactional
  public void sweepDespawns(Instant now){
    for (Colossus c : colossi.findByStatus(ColossusStatus.ROAMING)){
      if (!c.getDespawnAt().isAfter(now)){
        c.setStatus(ColossusStatus.DESPAWNED);            // survived the hour — no rewards
        colossi.save(c);
      }
    }
  }

  // ---- position interpolation ------------------------------------------------------------------

  /** Current angle along the arc for {@code now} (clamped to the spawn window). */
  private double angleAt(Colossus c, Instant now){
    double total = c.getDespawnAt().getEpochSecond() - c.getSpawnedAt().getEpochSecond();
    double elapsed = Math.max(0, Math.min(total, now.getEpochSecond() - c.getSpawnedAt().getEpochSecond()));
    double frac = total <= 0 ? 0 : elapsed / total;
    return c.getStartAngle() + c.getArcSpan() * frac;
  }

  private int[] positionAt(Colossus c, Instant now){
    double a = angleAt(c, now);
    return new int[]{ (int)Math.round(c.getCenterX() + Math.cos(a) * c.getRadius()),
                      (int)Math.round(c.getCenterY() + Math.sin(a) * c.getRadius()) };
  }

  // ---- attack dispatch -------------------------------------------------------------------------

  /** A unit may engage the sea Colossus if it fights on the SEA layer OR flies (Fairies from the air). */
  private boolean canEngage(UnitType u){
    return u.getCombatLayer() == CombatLayer.SEA || u.getMovementClass() == MovementClass.FLYING;
  }

  /**
   * Dispatch a strike: validate the alliance + sea/flying forces, remove them from the garrison, and
   * send an OUT movement to the Colossus's projected position. Resolved on arrival by {@link #onArrive}.
   */
  @Transactional
  public Map<String,Object> attack(Long playerId, Long colossusId, Long cityId, Map<String,Integer> troops, Long heroId){
    Player p = players.findById(playerId).orElseThrow();
    if (p.getAllianceId() == null)
      throw new IllegalStateException("Join an alliance to fight the Colossus — rewards are paid to alliance treasuries");
    Colossus c = colossi.findById(colossusId).orElseThrow(() -> new IllegalArgumentException("Colossus not found"));
    if (c.getStatus() != ColossusStatus.ROAMING) throw new IllegalStateException("That Colossus is no longer here");
    City city = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(city.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    if (troops == null || troops.isEmpty()) throw new IllegalArgumentException("Select sea or flying units to send");

    Map<String,Integer> garrison = new HashMap<>();
    for (CityUnit cu : units.findByCityId(cityId)) garrison.merge(cu.getType().toUpperCase(), cu.getCount(), Integer::sum);
    Map<String,Integer> sent = new LinkedHashMap<>();
    for (var e : troops.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      UnitType u = catalog.get(e.getKey());
      if (!canEngage(u)) throw new IllegalStateException("Only fleet or flying units can engage the Colossus: " + u.getName());
      if (garrison.getOrDefault(u.getName().toUpperCase(), 0) < e.getValue()) throw new IllegalStateException("Not enough " + u.getName());
      sent.put(u.getName(), e.getValue());
    }
    if (sent.isEmpty()) throw new IllegalArgumentException("Select sea or flying units to send");
    // remove the sent forces from the garrison (now marching)
    for (var e : sent.entrySet()){
      String name = e.getKey();
      CityUnit cu = units.findByCityId(cityId).stream().filter(u -> u.getType().equalsIgnoreCase(name)).findFirst().orElseThrow();
      cu.setCount(cu.getCount() - e.getValue()); units.save(cu);
    }

    long secs = travelSeconds(city, c, sent);
    Movement m = new Movement();
    m.setWorldId(city.getWorldId()); m.setPlayerId(playerId); m.setSourceCityId(cityId);
    m.setTargetColossusId(c.getId()); m.setPhase(MovementPhase.OUT);
    m.setUnits(new HashMap<>(sent)); m.setArriveAt(Instant.now().plusSeconds(secs));
    // remember the chosen hero on the loot map key-space is unsuitable; stash via a dedicated movement? we keep it simple: no hero on Colossus runs unless idle here
    Movement saved = movements.save(m);

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("ok", true); out.put("status", "DISPATCHED");
    out.put("movementId", saved.getId()); out.put("travelSeconds", secs);
    out.put("arriveAt", saved.getArriveAt().toString());
    return out;
  }

  /** Travel seconds from a city to the Colossus's projected position (px distance → tiles → pace). */
  private long travelSeconds(City from, Colossus c, Map<String,Integer> sent){
    Island isl = islands.findById(from.getIslandId()).orElse(null);
    int[] pos = positionAt(c, Instant.now());
    double px = isl == null ? c.getRadius() : Math.hypot(isl.getPx() - pos[0], isl.getPy() - pos[1]);
    double tiles = Math.max(8.0, px / 12.0);                 // PX_PER_TILE = 12
    int mpt = travel.slowestMinutesPerTile(sent);
    long secs = (long)Math.round(tiles * Math.max(1, mpt) * 0.05);   // SECONDS_PER_TILE_MINUTE
    if (from.getRace() != null) secs = (long)(secs * from.getRace().travelMult);
    secs = (long)(secs * library.effects(from.getId()).travelMult());
    return Math.max(5, secs);
  }

  // ---- arrival resolution ----------------------------------------------------------------------

  /** An OUT Colossus movement lands: resolve combat, deal damage to the shared pool, tally the
   *  attacker's alliance, write a report, and march survivors home. Called from {@link TickScheduler}. */
  @Transactional
  public void onArrive(Movement m, Instant now){
    Map<String,Integer> sent = new LinkedHashMap<>(m.getUnits());
    Colossus c = m.getTargetColossusId() == null ? null : colossi.findById(m.getTargetColossusId()).orElse(null);
    if (c == null || c.getStatus() != ColossusStatus.ROAMING || !c.getDespawnAt().isAfter(now)){
      marchHome(m, sent, now); return;                       // gone (killed / despawned) before arrival
    }
    City src = cities.findById(m.getSourceCityId()).orElse(null);
    Race atkRace = src != null ? src.getRace() : null;
    LibraryService.LibEffects atkLib = src != null ? library.effects(src.getId()) : LibraryService.LibEffects.none();
    double atkMult = (atkRace != null ? atkRace.attackMult : 1.0) * atkLib.attackMult();
    CombatEngine.Mods mods = new CombatEngine.Mods(atkMult, 1, 1, 1, 1, 1, 1);
    Element atkElement = atkRace != null ? atkRace.element : Element.FIRE;

    // The Colossus counterattacks as a heavy synthetic garrison (scaled by tier) so engaging costs
    // real troops; the elemental PROFILE decides how much damage the attack deals to the HP pool.
    Map<String,Integer> defenders = colossusGarrison(c.getTier());
    CombatEngine.Result r = combat.resolve(sent, atkElement, defenders, mods);

    long damage = computeDamage(r, c);
    damage = Math.min(damage, c.getCurrentHealth());
    c.setCurrentHealth(c.getCurrentHealth() - damage);

    // tally the attacker's alliance
    Long allianceId = m.getPlayerId() == null ? null : players.findById(m.getPlayerId()).map(Player::getAllianceId).orElse(null);
    if (allianceId != null && damage > 0){
      ColossusDamage cd = damages.findByColossusIdAndAllianceId(c.getId(), allianceId)
          .orElseGet(() -> new ColossusDamage(c.getId(), allianceId));
      cd.setAccumulatedDamage(cd.getAccumulatedDamage() + damage);
      cd.setLastContributionAt(now);
      damages.save(cd);
    }

    boolean defeated = c.getCurrentHealth() <= 0;
    if (defeated){ c.setStatus(ColossusStatus.DEFEATED); }
    colossi.save(c);

    // battle report — the Colossus is the PvE "defender"
    Map<String,Long> none = new LinkedHashMap<>(Map.of("WOOD",0L,"STONE",0L,"WHEAT",0L));
    reports.createNodeReport(m, new BattleResult(r.outcome(),
        sent, r.attackerLost(), r.attackerSurvived(),
        defenders, r.defenderLost(), r.defenderSurvived(),
        none, r.attackerAttackPower(), r.defenderDefencePower(), r.siegeDamage(),
        r.attackByElement(), r.defenseByElement()),
        null, "🌊 " + c.getName() + " (dealt " + damage + " dmg)", null);

    if (defeated) distributeRewards(c, now);
    marchHome(m, new LinkedHashMap<>(r.attackerSurvived()), now);
  }

  /** Damage to the HP pool: total post-mods attack scaled by the matchup of the attack element vs the
   *  Colossus's defence of that element (weak element → far more damage; resisted → far less). */
  private long computeDamage(CombatEngine.Result r, Colossus c){
    long dmg = 0;
    double avgDef = DEFENSE_BULK / 4.0;
    for (var e : r.attackByElement().entrySet()){
      Element el = Element.valueOf(e.getKey());
      int def = Math.max(1, c.defenseOf(el));
      dmg += Math.round(e.getValue() * (avgDef / def));     // effectiveness = average / this element's defence
    }
    return Math.max(0, dmg);
  }

  /** The Colossus's counterattack force, scaled by tier (uses the always-present Human base roster). */
  private static Map<String,Integer> colossusGarrison(int tier){
    Map<String,Integer> d = new LinkedHashMap<>();
    d.put("HOPLITE", 600 * tier);
    d.put("ARCHER",  400 * tier);
    d.put("HORSEMAN",150 * tier);
    return d;
  }

  private void marchHome(Movement m, Map<String,Integer> army, Instant now){
    if (army.isEmpty()) return;
    Movement ret = new Movement();
    ret.setWorldId(m.getWorldId()); ret.setPlayerId(m.getPlayerId()); ret.setSourceCityId(m.getSourceCityId());
    ret.setTargetColossusId(m.getTargetColossusId()); ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(new HashMap<>(army));
    long secs = Math.max(5, now.getEpochSecond() - m.getDepartAt().getEpochSecond());
    ret.setArriveAt(now.plusSeconds(secs));
    movements.save(ret);
  }

  // ---- reward distribution (proportional, per alliance) ----------------------------------------

  @Transactional
  public void distributeRewards(Colossus c, Instant now){
    List<ColossusDamage> rows = damages.findByColossusIdOrderByAccumulatedDamageDesc(c.getId());
    long total = rows.stream().mapToLong(ColossusDamage::getAccumulatedDamage).sum();
    if (total <= 0) return;
    long poolPerRes = 200_000L * c.getTier();                 // wood/stone/wheat pool, scales with tier
    for (ColossusDamage row : rows){
      double share = (double) row.getAccumulatedDamage() / total;
      alliances.findById(row.getAllianceId()).ifPresent(a -> {
        long give = Math.round(poolPerRes * share);
        a.setTreasuryWood(a.getTreasuryWood() + give);
        a.setTreasuryStone(a.getTreasuryStone() + give);
        a.setTreasuryWheat(a.getTreasuryWheat() + give);
        alliances.save(a);
      });
    }
    // hero-item rewards: a few rare relics handed to the top alliances, weighted by damage share.
    int items = 1 + c.getTier();
    for (int i = 0; i < items && i < rows.size(); i++){
      ColossusDamage row = rows.get(i);                       // top contributors get the relics
      alliances.findById(row.getAllianceId())
        .flatMap(a -> Optional.ofNullable(a.getLeaderId()))
        .ifPresent(leaderId -> {                              // delivered to the alliance leader to assign
          HeroItem relic = itemFactory.ofRarity(leaderId, HeroItem.Rarity.RARE, rnd);
          heroItems.save(relic);
        });
    }
  }

  // ---- read views ------------------------------------------------------------------------------

  /** Active Colossi in the world, with the requesting player's alliance damage share. */
  @Transactional(readOnly = true)
  public List<Map<String,Object>> listActive(Long worldId, Long playerId){
    Long myAlliance = players.findById(playerId).map(Player::getAllianceId).orElse(null);
    List<Map<String,Object>> out = new ArrayList<>();
    for (Colossus c : colossi.findByWorldIdAndStatus(worldId, ColossusStatus.ROAMING))
      out.add(summary(c, myAlliance, Instant.now()));
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String,Object> detail(Long colossusId, Long playerId){
    Colossus c = colossi.findById(colossusId).orElseThrow(() -> new IllegalArgumentException("Colossus not found"));
    Long myAlliance = players.findById(playerId).map(Player::getAllianceId).orElse(null);
    Map<String,Object> m = summary(c, myAlliance, Instant.now());
    m.put("leaderboard", leaderboard(colossusId));
    return m;
  }

  private Map<String,Object> summary(Colossus c, Long myAlliance, Instant now){
    int[] pos = positionAt(c, now);
    long total = damages.findByColossusIdOrderByAccumulatedDamageDesc(c.getId())
        .stream().mapToLong(ColossusDamage::getAccumulatedDamage).sum();
    long mine = myAlliance == null ? 0 :
        damages.findByColossusIdAndAllianceId(c.getId(), myAlliance).map(ColossusDamage::getAccumulatedDamage).orElse(0L);
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", c.getId()); m.put("name", c.getName()); m.put("tier", c.getTier());
    m.put("status", c.getStatus().name());
    m.put("maxHealth", c.getMaxHealth()); m.put("currentHealth", Math.max(0, c.getCurrentHealth()));
    m.put("x", pos[0]); m.put("y", pos[1]);
    m.put("despawnAt", c.getDespawnAt().toString());
    m.put("attackElement", c.getAttackElement().name());
    Map<String,Integer> def = new LinkedHashMap<>();
    def.put("FIRE", c.getDefenseFire()); def.put("WIND", c.getDefenseWind());
    def.put("EARTH", c.getDefenseEarth()); def.put("WATER", c.getDefenseWater());
    m.put("defense", def);
    m.put("totalDamage", total);
    m.put("myAllianceDamage", mine);
    m.put("myAllianceSharePct", total > 0 ? Math.round(mine * 1000.0 / total) / 10.0 : 0.0);
    m.put("rewardPoolPerResource", 200_000L * c.getTier());
    return m;
  }

  @Transactional(readOnly = true)
  public List<Map<String,Object>> leaderboard(Long colossusId){
    List<ColossusDamage> rows = damages.findByColossusIdOrderByAccumulatedDamageDesc(colossusId);
    long total = rows.stream().mapToLong(ColossusDamage::getAccumulatedDamage).sum();
    List<Map<String,Object>> out = new ArrayList<>();
    int rank = 1;
    for (ColossusDamage row : rows){
      Alliance a = alliances.findById(row.getAllianceId()).orElse(null);
      Map<String,Object> e = new LinkedHashMap<>();
      e.put("rank", rank++);
      e.put("allianceId", row.getAllianceId());
      e.put("allianceName", a == null ? "—" : a.getName());
      e.put("allianceTag", a == null ? "" : a.getTag());
      e.put("damage", row.getAccumulatedDamage());
      e.put("sharePct", total > 0 ? Math.round(row.getAccumulatedDamage() * 1000.0 / total) / 10.0 : 0.0);
      out.add(e);
    }
    return out;
  }
}
