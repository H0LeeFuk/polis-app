package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Per-player bandit camp: each player gets a single camp on their first (capital) city's island,
 * created when they enter the server. Levels are beaten in order; clearing level 10 puts the camp
 * into a respawn cooldown, after which it returns to level 1.
 *
 * <p>A raid is no longer instant: {@link #attack} deducts the army and dispatches an OUT
 * movement to the camp; {@link #onArrive} (driven by {@link TickScheduler}) resolves the
 * battle, writes a Battle Report, and marches the survivors home carrying any looted
 * resources. Unit rewards join the returning army; resource rewards ride home as loot.
 */
@Service
public class BanditCampService {
  private final BanditCampRepo camps;
  private final BanditCampLevelRepo levels;
  private final CityRepo cities;
  private final UnitRepo units;
  private final CombatEngine combat;
  private final UnitCatalog catalog;
  private final MissionService missions;
  private final MovementRepo movements;
  private final BattleReportService reports;
  private final LibraryService library;
  private final TravelTimeService travel;

  @Value("${polis.bandit-respawn-hours:24}") private long respawnHours;

  public BanditCampService(BanditCampRepo camps, BanditCampLevelRepo levels, CityRepo cities, UnitRepo units,
                           CombatEngine combat, UnitCatalog catalog, MissionService missions,
                           MovementRepo movements, BattleReportService reports, LibraryService library,
                           TravelTimeService travel){
    this.camps=camps; this.levels=levels; this.cities=cities; this.units=units;
    this.combat=combat; this.catalog=catalog; this.missions=missions;
    this.movements=movements; this.reports=reports; this.library=library; this.travel=travel;
  }

  /**
   * Creates the player's single bandit camp on their capital island — once, when they enter the
   * server (account setup / backfill). Idempotent: a player who already has a camp is left alone.
   */
  @Transactional
  public void ensureForPlayer(Long playerId){
    if (!camps.findByPlayerId(playerId).isEmpty()) return;
    City capital = cities.findByPlayerIdAndCapitalTrue(playerId)
        .orElseGet(() -> cities.findByPlayerId(playerId).stream().findFirst().orElse(null));
    if (capital == null) return;
    BanditCamp n = new BanditCamp();
    n.setIslandId(capital.getIslandId()); n.setPlayerId(playerId); n.setCurrentLevel(1);
    camps.save(n);
  }

  /** Apply a due respawn (level 10 cleared, cooldown elapsed) and persist. */
  private BanditCamp applyRespawn(BanditCamp c){
    if (c.getRespawnAt() != null && !c.getRespawnAt().isAfter(Instant.now())){
      c.setCurrentLevel(1); c.setRespawnAt(null); c.setDefeatedAt(null); camps.save(c);
    }
    return c;
  }

  @Transactional
  public Map<String,Object> dto(Long islandId, Long playerId){
    BanditCamp c = camps.findByIslandIdAndPlayerId(islandId, playerId).map(this::applyRespawn).orElse(null);
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("islandId", islandId);
    if (c == null){ m.put("status", "NONE"); return m; }   // the camp lives only at the player's first city
    boolean defeated = c.getRespawnAt() != null;
    m.put("currentLevel", c.getCurrentLevel());
    m.put("maxLevel", 10);
    m.put("status", defeated ? "DEFEATED" : "ACTIVE");
    m.put("respawnAt", c.getRespawnAt() == null ? null : c.getRespawnAt().toString());
    if (!defeated){
      BanditCampLevel lv = level(c.getCurrentLevel());
      m.put("defenderTroops", lv.getDefenderTroops());
      m.put("rewardType", lv.getRewardType().name());
      m.put("rewardPayload", lv.getRewardPayload());
      m.put("description", lv.getDescription());
    }
    return m;
  }

  private BanditCampLevel level(int lv){
    return levels.findById(lv).orElseThrow(() -> new IllegalStateException("Camp level not configured: " + lv));
  }

  /**
   * Dispatch a raid: validate + remove the army from the garrison and send an OUT movement to
   * the camp. The battle is resolved on arrival by {@link #onArrive}. Returns the dispatch info
   * (movement id + ETA); the outcome arrives later as a Battle Report.
   */
  @Transactional
  public Map<String,Object> attack(Long playerId, Long islandId, Long cityId, Map<String,Integer> troops){
    City city = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(city.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    if (!Objects.equals(city.getIslandId(), islandId)) throw new IllegalStateException("That city is not on this island");
    if (troops == null || troops.isEmpty()) throw new IllegalArgumentException("Select at least one unit");

    BanditCamp camp = camps.findByIslandIdAndPlayerId(islandId, playerId).map(this::applyRespawn)
        .orElseThrow(() -> new IllegalStateException("You have no bandit camp on this island"));
    if (camp.getRespawnAt() != null) throw new IllegalStateException("The camp has been defeated — it is rebuilding");

    // validate then remove the army from the garrison (it is now marching)
    Map<String,Integer> sent = new LinkedHashMap<>();
    for (var e : troops.entrySet()){
      if (e.getValue() == null || e.getValue() <= 0) continue;
      String name = catalog.get(e.getKey()).getName();
      CityUnit cu = units.findByCityId(cityId).stream().filter(u -> u.getType().equalsIgnoreCase(name)).findFirst()
          .orElseThrow(() -> new IllegalStateException("Not enough " + name));
      if (cu.getCount() < e.getValue()) throw new IllegalStateException("Not enough " + name);
      sent.put(e.getKey(), e.getValue());
    }
    if (sent.isEmpty()) throw new IllegalArgumentException("Select at least one unit");
    for (var e : sent.entrySet()){
      String name = catalog.get(e.getKey()).getName();
      CityUnit cu = units.findByCityId(cityId).stream().filter(u -> u.getType().equalsIgnoreCase(name)).findFirst().orElseThrow();
      cu.setCount(cu.getCount() - e.getValue()); units.save(cu);
    }

    // march time: same island as the camp, paced by the slowest unit, with race + Library bonuses
    long secs = travel.seconds(city.getIslandId(), islandId, travel.slowestMinutesPerTile(sent));
    if (city.getRace() != null) secs = (long)(secs * city.getRace().travelMult);
    secs = (long)(secs * library.effects(cityId).travelMult());
    secs = Math.max(5, secs);

    Movement m = new Movement();
    m.setWorldId(city.getWorldId()); m.setPlayerId(playerId); m.setSourceCityId(cityId);
    m.setTargetCampId(camp.getId()); m.setTargetIslandId(islandId); m.setPhase(MovementPhase.OUT);
    m.setUnits(new HashMap<>(sent)); m.setArriveAt(Instant.now().plusSeconds(secs));
    Movement saved = movements.save(m);

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("status", "DISPATCHED");
    out.put("movementId", saved.getId());
    out.put("travelSeconds", secs);
    out.put("arriveAt", saved.getArriveAt().toString());
    return out;
  }

  /**
   * An OUT bandit movement reaches the camp: resolve combat, write the report, then march the
   * survivors home with any loot. Called from {@link TickScheduler} inside the tick transaction.
   */
  @Transactional
  public void onArrive(Movement m, Instant now){
    Map<String,Integer> sent = new LinkedHashMap<>(m.getUnits());
    BanditCamp camp = m.getTargetCampId() == null ? null : camps.findById(m.getTargetCampId()).orElse(null);
    if (camp == null){ marchHome(m, sent, null, now); return; }
    camp = applyRespawn(camp);                                // apply any due respawn (this player's camp)
    if (camp.getRespawnAt() != null){ marchHome(m, sent, null, now); return; } // cleared by someone else mid-march

    BanditCampLevel lv = level(camp.getCurrentLevel());
    int foughtLevel = lv.getLevel();

    // attacker race + Library bonuses (no defending hero/race — these are barbarians)
    City src = cities.findById(m.getSourceCityId()).orElse(null);
    Race atkRace = src != null ? src.getRace() : null;
    LibraryService.LibEffects atkLib = src != null ? library.effects(src.getId()) : LibraryService.LibEffects.none();
    CombatEngine.Mods mods = new CombatEngine.Mods(
        (atkRace != null ? atkRace.attackMult : 1.0) * atkLib.attackMult(), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    Element atkElement = atkRace != null ? atkRace.element : Element.FIRE;

    Map<String,Integer> defenders = new LinkedHashMap<>(lv.getDefenderTroops());
    CombatEngine.Result r = combat.resolve(sent, atkElement, defenders, mods);
    boolean win = r.outcome() == BattleOutcome.VICTORY;

    Map<String,Long> resourcesStolen = new LinkedHashMap<>();
    resourcesStolen.put("WOOD",0L); resourcesStolen.put("STONE",0L); resourcesStolen.put("WHEAT",0L);
    Map<String,Long> loot = null;
    Map<String,Integer> returning = new LinkedHashMap<>(r.attackerSurvived());

    if (win){
      if (m.getPlayerId() != null) missions.record(m.getPlayerId(), MissionObjectiveType.ATTACK_BANDIT_CAMP, 1);
      // reward: resource keys ride home as loot; unit keys join the returning army
      for (var e : lv.getRewardPayload().entrySet()){
        long amt = e.getValue();
        switch (e.getKey().toLowerCase()){
          case "wood"   -> resourcesStolen.merge("WOOD", amt, Long::sum);
          case "stone"  -> resourcesStolen.merge("STONE", amt, Long::sum);
          case "silver", "wheat" -> resourcesStolen.merge("WHEAT", amt, Long::sum);
          default       -> returning.merge(e.getKey(), (int) amt, Integer::sum);
        }
      }
      double lootMult = (atkRace != null ? atkRace.lootMult : 1.0) * atkLib.lootMult();
      if (lootMult != 1.0) resourcesStolen.replaceAll((k,v) -> (long)(v * lootMult));
      if (resourcesStolen.values().stream().anyMatch(v -> v > 0)) loot = new HashMap<>(resourcesStolen);

      // advance the camp / start its respawn cooldown
      if (camp.getCurrentLevel() >= 10){
        camp.setDefeatedAt(now); camp.setRespawnAt(now.plusSeconds(respawnHours * 3600));
      } else {
        camp.setCurrentLevel(camp.getCurrentLevel() + 1);
      }
      camps.save(camp);
    }

    // battle report — the camp is the "defender" (Barbarians, no player id)
    reports.createNodeReport(m, new BattleResult(r.outcome(),
        sent, r.attackerLost(), r.attackerSurvived(),
        defenders, r.defenderLost(), r.defenderSurvived(),
        resourcesStolen, r.attackerAttackPower(), r.defenderDefencePower(), r.siegeDamage(),
        r.attackByElement(), r.defenseByElement()),
        null, "🏴‍☠️ Bandit Camp · Lvl " + foughtLevel, null);

    marchHome(m, returning, loot, now);
  }

  /** Send the surviving army (and any loot) back to its home city; nothing returns if wiped. */
  private void marchHome(Movement m, Map<String,Integer> army, Map<String,Long> loot, Instant now){
    if (army.isEmpty() && (loot == null || loot.isEmpty())) return;
    Movement ret = new Movement();
    ret.setWorldId(m.getWorldId()); ret.setPlayerId(m.getPlayerId()); ret.setSourceCityId(m.getSourceCityId());
    ret.setTargetCampId(m.getTargetCampId()); ret.setTargetIslandId(m.getTargetIslandId());
    ret.setPhase(MovementPhase.RETURN);
    ret.setUnits(new HashMap<>(army)); ret.setLoot(loot);
    long secs = Math.max(5, now.getEpochSecond() - m.getDepartAt().getEpochSecond());
    ret.setArriveAt(now.plusSeconds(secs));
    movements.save(ret);
  }
}
