package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.CityRepo;
import com.polis.repo.HeroRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Owns the account heroes (now TWO per player: LEO and CELINE). Each hero levels, equips,
 * arms skills, marches and wounds independently. All player actions are scoped to a heroId.
 *
 * <p>Race flavour: LEO (Humans) is a balanced generalist; CELINE (Fairies) scales harder on
 * Cunning (more loot, faster travel) but defends more fragilely — so the player picks a hero
 * to fit the job.
 */
@Service
public class HeroService {
  static final double LEADERSHIP_STEP = 0.02;
  static final double CUNNING_LOOT_STEP = 0.03;
  static final double CUNNING_TRAVEL_STEP = 0.015;
  static final double VALOR_LOSS_STEP = 0.02;
  static final double VALOR_WOUND_STEP = 0.05;
  static final long WOUND_BASE_SECONDS = 8 * 3600;
  static final double WOUND_THRESHOLD = 0.70;
  // Fairy (Celine) racial scaling
  static final double FAIRY_LOOT_STEP = 0.045;
  static final double FAIRY_TRAVEL_STEP = 0.022;
  static final double FAIRY_DEFENSE_MULT = 0.90;

  private final HeroRepo heroes;
  private final CityRepo cities;
  private final HeroEquipmentService equipment;

  public HeroService(HeroRepo heroes, CityRepo cities, HeroEquipmentService equipment){
    this.heroes = heroes; this.cities = cities; this.equipment = equipment;
  }

  // --- lookup / lifecycle -----------------------------------------------------

  @Transactional(readOnly = true)
  public List<Hero> list(Long playerId){
    List<Hero> hs = heroes.findByOwnerPlayerId(playerId);
    hs.sort(Comparator.comparing(Hero::getHeroKey));   // LEO before CELINE
    return hs;
  }

  /** A hero owned by the player (any unlock/state) — for read & management. */
  public Hero requireOwned(Long playerId, Long heroId){
    Hero h = heroes.findById(heroId).orElseThrow(() -> new IllegalArgumentException("Hero not found"));
    if (!Objects.equals(h.getOwnerPlayerId(), playerId)) throw new IllegalStateException("Not your hero");
    return h;
  }

  @Transactional(readOnly = true)
  public Optional<Hero> byKey(Long playerId, HeroKey key){ return heroes.findByOwnerPlayerIdAndHeroKey(playerId, key); }

  /** Create a fixed hero for a player (used by account setup / backfill). */
  @Transactional
  public Hero create(Long playerId, HeroKey key, Race race, String name, boolean unlocked, Long stationedCityId){
    Hero h = new Hero();
    h.setOwnerPlayerId(playerId);
    h.setHeroKey(key); h.setRace(race); h.setName(name); h.setUnlocked(unlocked);
    h.setXpToNextLevel(xpForLevel(1));
    h.setStationedCityId(stationedCityId);
    return heroes.save(h);
  }

  // --- player actions (all heroId-scoped) -------------------------------------

  @Transactional
  public Hero addAttributes(Long playerId, Long heroId, int leadership, int cunning, int valor){
    if (leadership < 0 || cunning < 0 || valor < 0) throw new IllegalArgumentException("Points cannot be negative");
    int sum = leadership + cunning + valor;
    Hero h = requireOwned(playerId, heroId);
    if (sum > h.getUnspentAttributePoints()) throw new IllegalStateException("Not enough attribute points");
    h.setAttrLeadership(h.getAttrLeadership() + leadership);
    h.setAttrCunning(h.getAttrCunning() + cunning);
    h.setAttrValor(h.getAttrValor() + valor);
    h.setUnspentAttributePoints(h.getUnspentAttributePoints() - sum);
    return heroes.save(h);
  }

  @Transactional
  public Hero station(Long playerId, Long heroId, Long cityId){
    Hero h = requireOwned(playerId, heroId);
    if (!h.isUnlocked()) throw new IllegalStateException("That hero is not unlocked yet");
    if (h.getState() != HeroState.IDLE) throw new IllegalStateException("The hero is not available right now");
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    h.setStationedCityId(cityId);
    return heroes.save(h);
  }

  /** Mark a hero as marching with an army; validated at dispatch. */
  @Transactional
  public Hero sendHero(Long playerId, Long heroId, Long originCityId, Long movementId){
    Hero h = requireOwned(playerId, heroId);
    if (!h.isUnlocked()) throw new IllegalStateException("That hero is not unlocked yet");
    if (h.getState() != HeroState.IDLE) throw new IllegalStateException("The hero is not available to march");
    if (!Objects.equals(h.getStationedCityId(), originCityId)) throw new IllegalStateException("The hero is not stationed in this city");
    h.setState(HeroState.MARCHING);
    h.setActiveMovementId(movementId);
    h.setStationedCityId(null);
    return heroes.save(h);
  }

  @Transactional
  public void arriveHome(Hero h, Long cityId){
    h.setStationedCityId(cityId);
    h.setActiveMovementId(null);
    if (h.getState() == HeroState.MARCHING) h.setState(HeroState.IDLE);
    heroes.save(h);
  }

  @Transactional
  public void recoverHealed(Instant now){
    for (Hero h : heroes.findByStateAndWoundedUntilLessThanEqual(HeroState.WOUNDED, now)){
      h.setState(HeroState.IDLE); h.setWoundedUntil(null); heroes.save(h);
    }
  }

  // --- equipment passthrough --------------------------------------------------

  @Transactional(readOnly = true)
  public List<Map<String,Object>> inventory(Long playerId){ return equipment.inventory(playerId); }

  @Transactional
  public void markInventorySeen(Long playerId){ equipment.markInventorySeen(playerId); }

  @Transactional
  public Hero equipItem(Long playerId, Long heroId, Long itemId){
    Hero h = requireOwned(playerId, heroId);
    equipment.equip(h, itemId);
    return heroes.save(h);
  }

  @Transactional
  public Hero unequipSlot(Long playerId, Long heroId, String slot){
    Hero h = requireOwned(playerId, heroId);
    equipment.unequip(h, HeroItem.Slot.valueOf(slot));
    return heroes.save(h);
  }

  @Transactional
  public Hero armSkill(Long playerId, Long heroId, String skillId){
    Hero h = requireOwned(playerId, heroId);
    HeroSkill skill;
    try { skill = HeroSkill.valueOf(skillId); } catch (Exception e){ throw new IllegalArgumentException("Unknown skill"); }
    if (!h.getUnlockedSkills().contains(skill.name()))
      throw new IllegalStateException(skill + " unlocks at level " + skill.unlockLevel);
    String until = h.getSkillCooldowns().get(skill.name());
    if (until != null && Instant.parse(until).isAfter(Instant.now()))
      throw new IllegalStateException(skill + " is still on cooldown");
    h.setArmedSkill(skill.name());
    return heroes.save(h);
  }

  // --- combat modifiers -------------------------------------------------------

  public CombatEngine.Mods offenseMods(Hero h){
    double attackMult = 1 + LEADERSHIP_STEP * h.getAttrLeadership() + equipment.attackPct(h);
    if (HeroSkill.CHARGE.name().equals(h.getArmedSkill())) attackMult += 0.25;
    double lossMult = Math.max(0.1, 1 - VALOR_LOSS_STEP * h.getAttrValor());
    if (HeroSkill.WAR_CRY.name().equals(h.getArmedSkill())) lossMult *= 0.5;
    return new CombatEngine.Mods(attackMult, 1, 1, lossMult);
  }

  public CombatEngine.Mods defenseMods(Hero h){
    double defMult = 1 + LEADERSHIP_STEP * h.getAttrLeadership() + equipment.defensePct(h);
    if (h.getRace() == Race.FAIRIES) defMult *= FAIRY_DEFENSE_MULT;   // Celine defends fragilely
    double sharpMult = HeroSkill.PHALANX.name().equals(h.getArmedSkill()) ? 1.30 : 1.0;
    sharpMult += equipment.defenseSharpPct(h);
    return new CombatEngine.Mods(1, defMult, sharpMult, 1);
  }

  public double lootMult(Hero h){
    double step = h.getRace() == Race.FAIRIES ? FAIRY_LOOT_STEP : CUNNING_LOOT_STEP;
    return 1 + step * h.getAttrCunning() + equipment.lootPct(h);
  }
  public double travelMult(Hero h){
    double step = h.getRace() == Race.FAIRIES ? FAIRY_TRAVEL_STEP : CUNNING_TRAVEL_STEP;
    double m = 1 - step * h.getAttrCunning() - equipment.travelPct(h);
    if (HeroSkill.FORCED_MARCH.name().equals(h.getArmedSkill())) m -= 0.40;
    return Math.max(0.2, m);
  }

  public int attackBonusPct(Hero h){ return (int)Math.round((offenseMods(h).attackMult() - 1) * 100); }
  public int lossReductionPct(Hero h){ return (int)Math.round((1 - offenseMods(h).attackerLossMult()) * 100); }

  // --- XP / leveling / wounds -------------------------------------------------

  public Integer grantXp(Hero h, long xp){
    if (xp <= 0) return null;
    h.setCurrentXp(h.getCurrentXp() + xp);
    int startLevel = h.getLevel();
    while (h.getCurrentXp() >= h.getXpToNextLevel()){
      h.setCurrentXp(h.getCurrentXp() - h.getXpToNextLevel());
      h.setLevel(h.getLevel() + 1);
      h.setUnspentAttributePoints(h.getUnspentAttributePoints() + 1);
      h.setXpToNextLevel(xpForLevel(h.getLevel()));
      unlockSkillsFor(h);
    }
    return h.getLevel() > startLevel ? h.getLevel() : null;
  }

  /** Grant XP to a hero by id and persist (used by mission rewards). */
  @Transactional
  public void grantXp(Long playerId, Long heroId, long xp){
    Hero h = requireOwned(playerId, heroId);
    grantXp(h, xp); heroes.save(h);
  }

  private void unlockSkillsFor(Hero h){
    for (HeroSkill s : HeroSkill.values())
      if (h.getLevel() >= s.unlockLevel && !h.getUnlockedSkills().contains(s.name()))
        h.getUnlockedSkills().add(s.name());
  }

  public String consumeArmedSkill(Hero h, Instant now){
    String armed = h.getArmedSkill();
    if (armed == null) return null;
    HeroSkill s = HeroSkill.valueOf(armed);
    h.getSkillCooldowns().put(s.name(), now.plusSeconds(s.cooldownHours * 3600L).toString());
    h.setArmedSkill(null);
    return armed;
  }

  public void wound(Hero h, Instant now){
    long secs = (long)(WOUND_BASE_SECONDS * Math.max(0.1, 1 - VALOR_WOUND_STEP * h.getAttrValor()));
    h.setState(HeroState.WOUNDED);
    h.setWoundedUntil(now.plusSeconds(secs));
  }

  public static long xpForLevel(int level){ return (long)Math.floor(100 * Math.pow(level, 1.5)); }

  // --- DTO --------------------------------------------------------------------

  public Map<String,Object> dto(Hero h){
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", h.getId());
    m.put("heroKey", h.getHeroKey().name());
    m.put("name", h.getName());
    m.put("race", h.getRace().name());
    m.put("unlocked", h.isUnlocked());
    m.put("level", h.getLevel());
    m.put("currentXp", h.getCurrentXp());
    m.put("xpToNextLevel", h.getXpToNextLevel());
    m.put("unspentAttributePoints", h.getUnspentAttributePoints());
    Map<String,Object> attrs = new LinkedHashMap<>();
    attrs.put("leadership", h.getAttrLeadership());
    attrs.put("cunning", h.getAttrCunning());
    attrs.put("valor", h.getAttrValor());
    m.put("attributes", attrs);
    m.put("state", h.getState().name());
    m.put("stationedCityId", h.getStationedCityId());
    m.put("stationedCityName", h.getStationedCityId()==null ? null :
        cities.findById(h.getStationedCityId()).map(City::getName).orElse(null));
    m.put("activeMovementId", h.getActiveMovementId());
    m.put("woundedUntil", h.getWoundedUntil()==null ? null : h.getWoundedUntil().toString());
    m.put("armedSkill", h.getArmedSkill());

    Instant now = Instant.now();
    List<Map<String,Object>> skills = new ArrayList<>();
    for (HeroSkill s : HeroSkill.values()){
      Map<String,Object> sm = new LinkedHashMap<>();
      boolean unlocked = h.getUnlockedSkills().contains(s.name());
      String cd = h.getSkillCooldowns().get(s.name());
      boolean onCd = cd != null && Instant.parse(cd).isAfter(now);
      sm.put("id", s.name());
      sm.put("unlockLevel", s.unlockLevel);
      sm.put("cooldownHours", s.cooldownHours);
      sm.put("unlocked", unlocked);
      sm.put("armed", s.name().equals(h.getArmedSkill()));
      sm.put("availableAt", onCd ? cd : null);
      skills.add(sm);
    }
    m.put("skills", skills);

    double lootStep = h.getRace()==Race.FAIRIES ? FAIRY_LOOT_STEP : CUNNING_LOOT_STEP;
    double travelStep = h.getRace()==Race.FAIRIES ? FAIRY_TRAVEL_STEP : CUNNING_TRAVEL_STEP;
    Map<String,Object> bonuses = new LinkedHashMap<>();
    bonuses.put("attackPct", (int)Math.round((LEADERSHIP_STEP*h.getAttrLeadership() + equipment.attackPct(h))*100));
    bonuses.put("defensePct", (int)Math.round((LEADERSHIP_STEP*h.getAttrLeadership() + equipment.defensePct(h))*100));
    bonuses.put("lootPct", (int)Math.round((lootStep*h.getAttrCunning() + equipment.lootPct(h))*100));
    bonuses.put("travelPct", (int)Math.round((travelStep*h.getAttrCunning() + equipment.travelPct(h))*100));
    bonuses.put("lossReductionPct", (int)Math.round(VALOR_LOSS_STEP*h.getAttrValor()*100));
    m.put("bonuses", bonuses);

    m.put("equipment", equipment.equippedDto(h));
    return m;
  }
}
