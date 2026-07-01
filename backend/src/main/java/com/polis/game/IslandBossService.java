package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Resource-island guardian bosses — a Colossus-style shared-HP boss but PER PLAYER and fixed to its
 * island. Players dispatch sea/flying forces (a marching {@code OUT} movement); on arrival the strike
 * deals damage to the shared pool (scaled by the elemental matchup), the troops take real losses to a
 * synthetic counter-garrison, and the damage is tallied to the attacking player. On defeat the reward
 * pool (resources + relics) is split by each player's share of total damage, then the boss respawns
 * after a cooldown. Distinct from the {@link ColossusService} world boss (roaming, alliance rewards).
 */
@Service
public class IslandBossService {
  private final IslandBossRepo bosses;
  private final IslandBossDamageRepo damages;
  private final CityRepo cities;
  private final UnitRepo units;
  private final PlayerRepo players;
  private final IslandRepo islands;
  private final CityService cityService;
  private final CombatEngine combat;
  private final UnitCatalog catalog;
  private final HeroItemRepo heroItems;
  private final ItemFactory itemFactory;
  private final BattleReportService reports;
  private final LibraryService library;
  private final MovementRepo movements;
  private final TravelTimeService travel;
  private final AllianceTierService tierProgress;
  private final Random rnd = new Random();

  @Value("${polis.boss-respawn-hours:12}") private long respawnHours;

  public IslandBossService(IslandBossRepo bosses, IslandBossDamageRepo damages, CityRepo cities, UnitRepo units,
                           PlayerRepo players, IslandRepo islands, CityService cityService,
                           CombatEngine combat, UnitCatalog catalog, HeroItemRepo heroItems,
                           ItemFactory itemFactory, BattleReportService reports, LibraryService library,
                           MovementRepo movements, TravelTimeService travel, AllianceTierService tierProgress){
    this.bosses=bosses; this.damages=damages; this.cities=cities; this.units=units; this.players=players;
    this.islands=islands; this.cityService=cityService; this.combat=combat; this.catalog=catalog;
    this.heroItems=heroItems; this.itemFactory=itemFactory; this.reports=reports; this.library=library;
    this.movements=movements; this.travel=travel; this.tierProgress=tierProgress;
  }

  // ---- tuning ----
  private static final long HEALTH_PER_LEVEL = 150_000L;   // shared HP = level * this
  private static final long REWARD_PER_LEVEL = 15_000L;    // wood/stone/wheat pool each = level * this
  private static final int  DEFENSE_BULK     = 400;        // total elemental defence, distribution rotates
  private static final Element[] ELEMS = Element.values();

  /** Race-themed land counter-garrison scaled by level — engaging the boss costs real troops. */
  public static Map<String,Integer> defendersFor(int level){
    Map<String,Integer> d = new LinkedHashMap<>();
    d.put("HOPLITE", 40 * level);
    d.put("SPEARMAN", 24 * level);
    d.put("ARCHER", 20 * level);
    d.put("HORSEMAN", 8 * level);
    return d;
  }

  private static final Map<Race,String> BOSS_NAME = Map.of(
      Race.HUMANS, "Warden of the Vale",
      Race.GIANTS, "Gravelmaw the Titan",
      Race.FAIRIES, "Thistlewing Queen",
      Race.NEWTS, "Tidemother Saru");
  public String bossName(Race r){ return BOSS_NAME.getOrDefault(r, "Island Guardian"); }

  /** Initialise a freshly-seeded boss: HP from level, ACTIVE, and a rolled elemental profile. Used by the seeder. */
  public void initSpawn(IslandBoss b){
    long hp = HEALTH_PER_LEVEL * Math.max(1, b.getLevel());
    b.setMaxHealth(hp); b.setCurrentHealth(hp);
    b.setStatus(IslandBossStatus.ACTIVE);
    b.setDefeatedAt(null); b.setRespawnAt(null);
    rollElementalProfile(b);
  }

  /** One weak element (~15%), one resisted (~40%), two medium — bulk constant; strikes with the weak one. */
  private void rollElementalProfile(IslandBoss b){
    List<Element> order = new ArrayList<>(Arrays.asList(ELEMS));
    Collections.shuffle(order, rnd);
    Element weak = order.get(0), strong = order.get(1);
    EnumMap<Element,Integer> def = new EnumMap<>(Element.class);
    def.put(weak,   Math.round(DEFENSE_BULK * 0.15f));
    def.put(strong, Math.round(DEFENSE_BULK * 0.40f));
    int rest = DEFENSE_BULK - def.get(weak) - def.get(strong);
    def.put(order.get(2), rest / 2);
    def.put(order.get(3), rest - rest / 2);
    b.setDefenseFire(def.get(Element.FIRE));   b.setDefenseWind(def.get(Element.WIND));
    b.setDefenseEarth(def.get(Element.EARTH)); b.setDefenseWater(def.get(Element.WATER));
    b.setAttackElement(weak);
  }

  // ---- respawn sweep (from the tick) ----
  @Transactional
  public void respawnDue(Instant now){
    for (IslandBoss b : bosses.findByStatus(IslandBossStatus.DEFEATED)){
      if (b.getRespawnAt() != null && !b.getRespawnAt().isAfter(now)){
        damages.deleteByBossId(b.getId());
        initSpawn(b);
        bosses.save(b);
      }
    }
  }

  // ---- attack dispatch (marching, sea/flying only) ----
  private boolean canEngage(UnitType u){
    return u.getCombatLayer() == CombatLayer.SEA || u.getMovementClass() == MovementClass.FLYING;
  }

  @Transactional
  public Map<String,Object> attack(Long playerId, Long islandId, Long cityId, Map<String,Integer> troops, Long heroId){
    IslandBoss b = bosses.findByIslandId(islandId).orElseThrow(() -> new IllegalStateException("No boss on this island"));
    if (b.getStatus() != IslandBossStatus.ACTIVE) throw new IllegalStateException("The boss is defeated — it returns later");
    City city = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(city.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    if (troops == null || troops.isEmpty()) throw new IllegalArgumentException("Select sea or flying units to send");

    Map<String,Integer> garrison = new HashMap<>();
    for (CityUnit cu : units.findByCityId(cityId)) garrison.merge(cu.getType().toUpperCase(), cu.getCount(), Integer::sum);
    Map<String,Integer> sent = new LinkedHashMap<>();
    for (var e : troops.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      UnitType u = catalog.get(e.getKey());
      if (!canEngage(u)) throw new IllegalStateException("Only fleet or flying units can engage the boss: " + u.getName());
      if (garrison.getOrDefault(u.getName().toUpperCase(), 0) < e.getValue()) throw new IllegalStateException("Not enough " + u.getName());
      sent.put(u.getName(), e.getValue());
    }
    if (sent.isEmpty()) throw new IllegalArgumentException("Select sea or flying units to send");
    for (var e : sent.entrySet()){
      CityUnit cu = units.findByCityId(cityId).stream().filter(u -> u.getType().equalsIgnoreCase(e.getKey())).findFirst().orElseThrow();
      cu.setCount(cu.getCount() - e.getValue()); units.save(cu);
    }

    long secs = travelSeconds(city, b, sent);
    Movement m = new Movement();
    m.setWorldId(city.getWorldId()); m.setPlayerId(playerId); m.setSourceCityId(cityId);
    m.setTargetBossId(b.getId()); m.setTargetIslandId(islandId); m.setPhase(MovementPhase.OUT);
    m.setUnits(new HashMap<>(sent)); m.setArriveAt(Instant.now().plusSeconds(secs));
    Movement saved = movements.save(m);

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("ok", true); out.put("status", "DISPATCHED");
    out.put("movementId", saved.getId()); out.put("travelSeconds", secs);
    out.put("arriveAt", saved.getArriveAt().toString());
    return out;
  }

  private long travelSeconds(City from, IslandBoss b, Map<String,Integer> sent){
    Island src = islands.findById(from.getIslandId()).orElse(null);
    Island dst = islands.findById(b.getIslandId()).orElse(null);
    double px = (src == null || dst == null) ? 500 : Math.hypot(src.getPx() - dst.getPx(), src.getPy() - dst.getPy());
    double tiles = Math.max(8.0, px / 12.0);
    int mpt = travel.slowestMinutesPerTile(sent);
    long secs = (long)Math.round(tiles * Math.max(1, mpt) * 0.05);
    if (from.getRace() != null) secs = (long)(secs * from.getRace().travelMult);
    secs = (long)(secs * library.effects(from.getId()).travelMult());
    return Math.max(5, secs);
  }

  // ---- arrival resolution ----
  @Transactional
  public void onArrive(Movement m, Instant now){
    Map<String,Integer> sent = new LinkedHashMap<>(m.getUnits());
    IslandBoss b = m.getTargetBossId() == null ? null : bosses.findById(m.getTargetBossId()).orElse(null);
    if (b == null || b.getStatus() != IslandBossStatus.ACTIVE){ marchHome(m, sent, now); return; }

    City src = cities.findById(m.getSourceCityId()).orElse(null);
    Race atkRace = src != null ? src.getRace() : null;
    LibraryService.LibEffects atkLib = src != null ? library.effects(src.getId()) : LibraryService.LibEffects.none();
    double atkMult = (atkRace != null ? atkRace.attackMult : 1.0) * atkLib.attackMult();
    CombatEngine.Mods mods = new CombatEngine.Mods(atkMult, 1, 1, 1, 1, 1, 1);
    Element atkElement = atkRace != null ? atkRace.element : Element.FIRE;

    Map<String,Integer> defenders = defendersFor(b.getLevel());
    CombatEngine.Result r = combat.resolve(sent, atkElement, defenders, mods);

    long damage = Math.min(computeDamage(r, b), b.getCurrentHealth());
    b.setCurrentHealth(b.getCurrentHealth() - damage);

    if (m.getPlayerId() != null && damage > 0){
      IslandBossDamage cd = damages.findByBossIdAndPlayerId(b.getId(), m.getPlayerId())
          .orElseGet(() -> new IslandBossDamage(b.getId(), m.getPlayerId()));
      cd.setAccumulatedDamage(cd.getAccumulatedDamage() + damage);
      cd.setLastContributionAt(now);
      damages.save(cd);
    }

    boolean defeated = b.getCurrentHealth() <= 0;
    if (defeated){
      b.setStatus(IslandBossStatus.DEFEATED);
      b.setDefeatedAt(now);
      b.setRespawnAt(now.plusSeconds(GameRules.fast(respawnHours * 3600)));
    }
    bosses.save(b);

    Map<String,Long> none = new LinkedHashMap<>(Map.of("WOOD",0L,"STONE",0L,"WHEAT",0L));
    reports.createNodeReport(m, new BattleResult(r.outcome(),
        sent, r.attackerLost(), r.attackerSurvived(),
        defenders, r.defenderLost(), r.defenderSurvived(),
        none, r.attackerAttackPower(), r.defenderDefencePower(), r.siegeDamage(),
        r.attackByElement(), r.defenseByElement()),
        null, "👹 " + b.getName() + " (dealt " + damage + " dmg)", null);

    if (defeated) distributeRewards(b, now);
    marchHome(m, new LinkedHashMap<>(r.attackerSurvived()), now);
  }

  private long computeDamage(CombatEngine.Result r, IslandBoss b){
    long dmg = 0;
    double avgDef = DEFENSE_BULK / 4.0;
    for (var e : r.attackByElement().entrySet()){
      Element el = Element.valueOf(e.getKey());
      int def = Math.max(1, b.defenseOf(el));
      dmg += Math.round(e.getValue() * (avgDef / def));
    }
    return Math.max(0, dmg);
  }

  private void marchHome(Movement m, Map<String,Integer> army, Instant now){
    if (army.isEmpty()) return;
    Movement ret = new Movement();
    ret.setWorldId(m.getWorldId()); ret.setPlayerId(m.getPlayerId()); ret.setSourceCityId(m.getSourceCityId());
    ret.setTargetBossId(m.getTargetBossId()); ret.setTargetIslandId(m.getTargetIslandId());
    ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(new HashMap<>(army));
    long secs = Math.max(5, now.getEpochSecond() - m.getDepartAt().getEpochSecond());
    ret.setArriveAt(now.plusSeconds(secs));
    movements.save(ret);
  }

  // ---- reward distribution (proportional, per player) ----
  @Transactional
  public void distributeRewards(IslandBoss b, Instant now){
    List<IslandBossDamage> rows = damages.findByBossIdOrderByAccumulatedDamageDesc(b.getId());
    long total = rows.stream().mapToLong(IslandBossDamage::getAccumulatedDamage).sum();
    if (total <= 0) return;
    long poolPerRes = REWARD_PER_LEVEL * Math.max(1, b.getLevel());
    for (IslandBossDamage row : rows){
      double share = (double) row.getAccumulatedDamage() / total;
      long give = Math.round(poolPerRes * share);
      City target = payCity(row.getPlayerId());
      if (target != null && give > 0){
        long cap = cityService.capacity(target.getId());
        target.setWood(Math.min(cap, target.getWood() + give));
        target.setStone(Math.min(cap, target.getStone() + give));
        target.setWheat(Math.min(cap, target.getWheat() + give));
        cities.save(target);
      }
    }
    // relics to the top contributors, weighted by damage rank
    int items = 1 + b.getTier();
    for (int i = 0; i < items && i < rows.size(); i++){
      Long pid = rows.get(i).getPlayerId();
      HeroItem.Rarity rarity = i == 0 ? HeroItem.Rarity.RARE : HeroItem.Rarity.COMMON;
      heroItems.save(itemFactory.ofRarity(pid, rarity, rnd));
    }
    // Alliance Tier Gate: credit each participating alliance with a boss kill for this island's tier
    Set<Long> creditedAlliances = new HashSet<>();
    for (IslandBossDamage row : rows){
      Long allianceId = players.findById(row.getPlayerId()).map(Player::getAllianceId).orElse(null);
      if (allianceId != null && creditedAlliances.add(allianceId))
        tierProgress.creditBossKill(allianceId, b.getTier());
    }
  }

  /** Where a player's boss-reward resources land: their capital, else their first city. */
  private City payCity(Long playerId){
    List<City> owned = cities.findByPlayerId(playerId);
    if (owned.isEmpty()) return null;
    return owned.stream().filter(City::isCapital).findFirst().orElse(owned.get(0));
  }

  // ---- read views ----
  @Transactional(readOnly = true)
  public Map<String,Object> dto(Long islandId){
    IslandBoss b = bosses.findByIslandId(islandId).orElse(null);
    if (b == null) return Map.of("exists", false);
    return summary(b, SecurityCurrentPlayer());
  }

  /** null-safe current player id (read views tolerate anonymous). */
  private Long SecurityCurrentPlayer(){
    try { return com.polis.config.SecurityConfig.currentPlayerId(); } catch (Exception e){ return null; }
  }

  private Map<String,Object> summary(IslandBoss b, Long playerId){
    List<IslandBossDamage> rows = damages.findByBossIdOrderByAccumulatedDamageDesc(b.getId());
    long total = rows.stream().mapToLong(IslandBossDamage::getAccumulatedDamage).sum();
    long mine = playerId == null ? 0 :
        damages.findByBossIdAndPlayerId(b.getId(), playerId).map(IslandBossDamage::getAccumulatedDamage).orElse(0L);

    Map<String,Object> m = new LinkedHashMap<>();
    m.put("exists", true);
    m.put("id", b.getId());
    m.put("islandId", b.getIslandId());
    m.put("name", b.getName());
    m.put("race", b.getRace().name());
    m.put("level", b.getLevel());
    m.put("tier", b.getTier());
    m.put("status", b.getStatus().name());
    m.put("maxHealth", b.getMaxHealth());
    m.put("currentHealth", Math.max(0, b.getCurrentHealth()));
    m.put("respawnAt", b.getRespawnAt() == null ? null : b.getRespawnAt().toString());
    m.put("attackElement", b.getAttackElement().name());
    Map<String,Integer> def = new LinkedHashMap<>();
    def.put("FIRE", b.getDefenseFire()); def.put("WIND", b.getDefenseWind());
    def.put("EARTH", b.getDefenseEarth()); def.put("WATER", b.getDefenseWater());
    m.put("defense", def);
    m.put("totalDamage", total);
    m.put("myDamage", mine);
    m.put("mySharePct", total > 0 ? Math.round(mine * 1000.0 / total) / 10.0 : 0.0);
    m.put("rewardPoolPerResource", REWARD_PER_LEVEL * Math.max(1, b.getLevel()));

    List<Map<String,Object>> lb = new ArrayList<>();
    int rank = 1;
    for (IslandBossDamage row : rows){
      Map<String,Object> e = new LinkedHashMap<>();
      e.put("rank", rank++);
      e.put("playerId", row.getPlayerId());
      e.put("playerName", players.findById(row.getPlayerId()).map(Player::getUsername).orElse("—"));
      e.put("damage", row.getAccumulatedDamage());
      e.put("sharePct", total > 0 ? Math.round(row.getAccumulatedDamage() * 1000.0 / total) / 10.0 : 0.0);
      lb.add(e);
    }
    m.put("leaderboard", lb);
    return m;
  }
}
