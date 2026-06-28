package com.polis.game;

import com.polis.auth.AuthService;
import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Seeds real, loginable accounts and a roster of distinct dummy players so every screen
 * (login, rankings, hero/inventory) is driven by database rows — never hard-coded data.
 *
 * <p>Creates two named accounts (bruno / mariana) plus eight dummy players, each with its own
 * race-flavoured capital, levelled heroes, rolled-and-equipped items and combat record. Runs
 * after {@link WorldSeeder} (Order 10) and {@link AccountBackfillRunner} (Order 20). Idempotent:
 * existing players are left in place (the two named accounts have their password re-asserted so
 * the documented credentials always log in).
 */
@Component
@Order(30)
public class DummyDataSeeder implements ApplicationRunner {

  private final AuthService auth;
  private final PlayerRepo players; private final WorldRepo worlds; private final IslandRepo islands;
  private final CityRepo cities; private final CityFactory cityFactory; private final BuildingRepo buildings;
  private final HeroRepo heroRepo; private final HeroItemRepo itemRepo; private final ItemFactory itemFactory;
  private final HeroService heroes; private final HeroEquipmentService equipment;
  private final AllianceRepo alliances; private final PasswordEncoder encoder;

  public DummyDataSeeder(AuthService auth, PlayerRepo players, WorldRepo worlds, IslandRepo islands,
                         CityRepo cities, CityFactory cityFactory, BuildingRepo buildings, HeroRepo heroRepo,
                         HeroItemRepo itemRepo, ItemFactory itemFactory, HeroService heroes,
                         HeroEquipmentService equipment, AllianceRepo alliances, PasswordEncoder encoder){
    this.auth=auth; this.players=players; this.worlds=worlds; this.islands=islands;
    this.cities=cities; this.cityFactory=cityFactory; this.buildings=buildings; this.heroRepo=heroRepo;
    this.itemRepo=itemRepo; this.itemFactory=itemFactory; this.heroes=heroes; this.equipment=equipment;
    this.alliances=alliances; this.encoder=encoder;
  }

  /** One dummy player's distinct profile. */
  private record Spec(String name, Race race, int combat, int level,
                      int leoLevel, boolean titaniaUnlocked, int titaniaLevel,
                      int items, int extraCities, int buildingBoost){}

  private static final List<Spec> DUMMIES = List.of(
      //         name        race          combat lvl  leo  titUnl titLvl items extraCities boost
      new Spec("Aetios",  Race.GIANTS,    950, 6,   8,  true,  5,    5,  1,  6),
      new Spec("Briseis", Race.FAIRIES,   300, 3,   4,  false, 1,    2,  0,  2),
      new Spec("Cyrene",  Race.NEWTS,    1500, 9,  12,  true,  8,    7,  2,  9),
      new Spec("Damon",   Race.HUMANS,    620, 5,   6,  false, 1,    3,  0,  4),
      new Spec("Elpis",   Race.FAIRIES,  1100, 7,   9,  true,  6,    6,  1,  7),
      new Spec("Galenos", Race.GIANTS,    200, 2,   3,  false, 1,    1,  0,  1),
      new Spec("Hespera", Race.NEWTS,     800, 6,   7,  true,  4,    4,  1,  5),
      new Spec("Ianthe",  Race.HUMANS,    450, 4,   5,  false, 1,    3,  0,  3)
  );

  @Override
  public void run(ApplicationArguments args){
    if (worlds.count() == 0) return;                 // world not seeded yet — nothing to attach to
    Long worldId = worlds.findAll().get(0).getId();

    ensureAccount("bruno",   "bruno@polis.local",   "bruno123");
    ensureAccount("mariana", "mariana@polis.local", "mariana123");

    List<Alliance> alls = alliances.findByWorldId(worldId);
    int i = 0;
    for (Spec s : DUMMIES){
      Long allianceId = alls.isEmpty() ? null : alls.get(i % alls.size()).getId();
      ensureDummy(s, worldId, allianceId);
      i++;
    }
  }

  /** Create the account if missing; always re-assert its password so the documented login works. */
  private void ensureAccount(String username, String email, String password){
    Optional<Player> existing = players.findByUsername(username);
    if (existing.isPresent()){
      Player p = existing.get();
      p.setPasswordHash(encoder.encode(password));   // BCrypt — guarantees credentials match
      players.save(p);
      return;
    }
    auth.register(username, email, password);         // BCrypt hash + capital + heroes + missions
  }

  private void ensureDummy(Spec s, Long worldId, Long allianceId){
    if (players.existsByUsername(s.name())) return;   // idempotent

    // base account: BCrypt password, capital, Leo + Titania, starter missions
    auth.register(s.name(), s.name().toLowerCase() + "@polis.local", s.name().toLowerCase() + "123");
    Player p = players.findByUsername(s.name()).orElseThrow();
    Random rnd = new Random(s.name().hashCode());

    // player record: combat record + alliance for the rankings
    p.setCombatPoints(s.combat());
    p.setLevel(s.level());
    p.setAllianceId(allianceId);
    players.save(p);

    // capital: race flavour + boosted buildings (drives points ranking)
    City capital = cities.findByPlayerIdAndCapitalTrue(p.getId()).orElseThrow();
    capital.setRace(s.race());
    boostCity(capital, s.buildingBoost(), rnd);

    // optional extra colonies for "X cities" variety
    for (int n = 0; n < s.extraCities(); n++){
      long[] slot = firstFreeSlot(worldId);
      if (slot == null) break;
      City colony = cityFactory.createPlayerCity(worldId, p.getId(), slot[0], (int) slot[1],
          s.name() + " Colony " + (n + 1), false, s.race());
      boostCity(colony, Math.max(1, s.buildingBoost() - 2), rnd);
    }

    // heroes: level Leo, optionally unlock + level Titania, spend attribute points by race flavour
    Hero leo = heroRepo.findByOwnerPlayerIdAndHeroKey(p.getId(), HeroKey.LEO).orElseThrow();
    levelHero(leo, s.leoLevel(), s.race());
    leo.setStationedCityId(capital.getId());
    heroRepo.save(leo);

    heroRepo.findByOwnerPlayerIdAndHeroKey(p.getId(), HeroKey.TITANIA).ifPresent(tit -> {
      if (s.titaniaUnlocked()){
        tit.setUnlocked(true);
        tit.setStationedCityId(capital.getId());
        levelHero(tit, s.titaniaLevel(), Race.FAIRIES);
      }
      heroRepo.save(tit);
    });

    // items: roll a distinct loadout, equip one of each slot onto Leo
    rollAndEquip(p.getId(), leo, s.items(), rnd);
  }

  /** Raise the city's buildings (capped at each type's max) and recompute its points/power. */
  private void boostCity(City c, int boost, Random rnd){
    Map<BuildingType,Integer> levels = new EnumMap<>(BuildingType.class);
    for (CityBuilding b : buildings.findByCityId(c.getId())){
      int lv = Math.min(b.getType().max, Math.max(b.getLevel(), boost + rnd.nextInt(3)));
      b.setLevel(lv);
      buildings.save(b);
      levels.put(b.getType(), lv);
    }
    int points = GameRules.cityPoints(levels);
    c.setPoints(points);
    c.setPower(Math.max(c.getPower(), points * 1.5));
    cities.save(c);
  }

  /** Bump a hero to the target level and spend its attribute points along its race's strength. */
  private void levelHero(Hero h, int targetLevel, Race race){
    int guard = 0;
    while (h.getLevel() < targetLevel && guard++ < 100)
      heroes.grantXp(h, h.getXpToNextLevel());        // one level per call, also unlocks skills

    int pts = h.getUnspentAttributePoints();
    int lead, cun, val;
    switch (race){
      case GIANTS  -> { val = pts * 3 / 5; lead = pts / 5; cun = pts - val - lead; }      // valor
      case FAIRIES -> { cun = pts * 3 / 5; lead = pts / 5; val = pts - cun - lead; }      // cunning
      case NEWTS   -> { lead = pts * 2 / 5; val = pts * 2 / 5; cun = pts - lead - val; }  // balanced
      default      -> { lead = pts * 3 / 5; val = pts / 5; cun = pts - lead - val; }      // humans: leadership
    }
    h.setAttrLeadership(h.getAttrLeadership() + lead);
    h.setAttrCunning(h.getAttrCunning() + cun);
    h.setAttrValor(h.getAttrValor() + val);
    h.setUnspentAttributePoints(0);
  }

  /** Roll {@code count} items into the player's inventory and equip one of each slot onto the hero. */
  private void rollAndEquip(Long playerId, Hero hero, int count, Random rnd){
    EnumMap<HeroItem.Slot,Long> firstOfSlot = new EnumMap<>(HeroItem.Slot.class);
    for (int n = 0; n < count; n++){
      HeroItem it = itemFactory.roll(playerId, rnd);
      it.setSeen(true);
      it = itemRepo.save(it);
      firstOfSlot.putIfAbsent(it.getSlot(), it.getId());
    }
    for (Long itemId : firstOfSlot.values()) equipment.equip(hero, itemId);
    heroRepo.save(hero);
  }

  private long[] firstFreeSlot(Long worldId){
    for (Island is : islands.findByWorldId(worldId))
      for (int s = 0; s < GameRules.SLOTS_PER_ISLAND; s++)
        if (cities.findByIslandIdAndSlot(is.getId(), s).isEmpty()) return new long[]{is.getId(), s};
    return null;
  }
}
