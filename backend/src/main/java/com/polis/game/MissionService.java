package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Mission engine: seeds each player's STARTER chain, advances progress from gameplay events
 * (build/train/attack/found/research/node), and grants rewards on claim. The capstone mission
 * auto-completes when 1–7 are done and, when claimed, unlocks the TITANIA hero.
 *
 * <p>Progress hooks ({@link #record}) are invoked directly by the relevant services rather than
 * via an event bus. Completing a mission freezes its progress, so repeated events are harmless.
 */
@Service
public class MissionService {
  private final MissionRepo missions;
  private final PlayerMissionRepo playerMissions;
  private final CityRepo cities;
  private final HeroService heroService;
  private final HeroRepo heroes;

  public MissionService(MissionRepo missions, PlayerMissionRepo playerMissions, CityRepo cities,
                        HeroService heroService, HeroRepo heroes){
    this.missions = missions; this.playerMissions = playerMissions; this.cities = cities;
    this.heroService = heroService; this.heroes = heroes;
  }

  // --- seeding ----------------------------------------------------------------

  /** Seed the STARTER chain for a player if not already present (order 1 = ACTIVE, rest LOCKED). */
  @Transactional
  public void seedForPlayer(Long playerId){
    if (playerMissions.countByPlayerId(playerId) > 0) return;
    List<Mission> chain = missions.findByChainOrderByOrderIndexAsc("STARTER");
    boolean first = true;
    for (Mission m : chain){
      PlayerMission pm = new PlayerMission();
      pm.setPlayerId(playerId); pm.setMissionId(m.getId());
      pm.setStatus(first ? PlayerMissionStatus.ACTIVE : PlayerMissionStatus.LOCKED);
      playerMissions.save(pm);
      first = false;
    }
  }

  // --- progress hooks ---------------------------------------------------------

  /**
   * Record an event toward the player's ACTIVE missions of this objective type.
   * For COUNT objectives {@code amount} accumulates; for LEVEL objectives it is the level reached.
   */
  @Transactional
  public void record(Long playerId, MissionObjectiveType type, int amount){
    if (playerId == null || amount <= 0) return;
    Map<Long,Mission> cfg = missionConfig();
    boolean anyCompleted = false;
    for (PlayerMission pm : playerMissions.findByPlayerId(playerId)){
      if (pm.getStatus() != PlayerMissionStatus.ACTIVE) continue;
      Mission m = cfg.get(pm.getMissionId());
      if (m == null || m.getObjectiveType() != type) continue;
      int next = m.getObjectiveType().countBased ? pm.getProgress() + amount : Math.max(pm.getProgress(), amount);
      pm.setProgress(Math.min(next, m.getObjectiveTarget()));
      if (pm.getProgress() >= m.getObjectiveTarget()){
        complete(pm, m);
        anyCompleted = true;
      } else {
        playerMissions.save(pm);
      }
    }
    if (anyCompleted) checkCapstone(playerId, cfg);
  }

  private void complete(PlayerMission pm, Mission m){
    pm.setStatus(PlayerMissionStatus.COMPLETED);
    pm.setProgress(m.getObjectiveTarget());
    pm.setCompletedAt(Instant.now());
    playerMissions.save(pm);
    // unlock the next mission in the chain
    for (Mission nxt : missions.findByChainOrderByOrderIndexAsc(m.getChain())){
      if (Objects.equals(nxt.getPrerequisiteMissionId(), m.getId())){
        playerMissions.findByPlayerIdAndMissionId(pm.getPlayerId(), nxt.getId()).ifPresent(npm -> {
          if (npm.getStatus() == PlayerMissionStatus.LOCKED){ npm.setStatus(PlayerMissionStatus.ACTIVE); playerMissions.save(npm); }
        });
      }
    }
  }

  /** The capstone (CHAIN_COMPLETE) auto-completes once every other STARTER mission is done. */
  private void checkCapstone(Long playerId, Map<Long,Mission> cfg){
    Mission capstone = null;
    for (Mission m : cfg.values())
      if ("STARTER".equals(m.getChain()) && m.getObjectiveType() == MissionObjectiveType.CHAIN_COMPLETE){ capstone = m; break; }
    if (capstone == null) return;
    PlayerMission capPm = playerMissions.findByPlayerIdAndMissionId(playerId, capstone.getId()).orElse(null);
    if (capPm == null || capPm.getStatus() == PlayerMissionStatus.COMPLETED || capPm.getStatus() == PlayerMissionStatus.CLAIMED) return;

    for (PlayerMission pm : playerMissions.findByPlayerId(playerId)){
      Mission m = cfg.get(pm.getMissionId());
      if (m == null || m == capstone || !"STARTER".equals(m.getChain())) continue;
      if (m.getObjectiveType() == MissionObjectiveType.CHAIN_COMPLETE) continue;
      if (pm.getStatus() != PlayerMissionStatus.COMPLETED && pm.getStatus() != PlayerMissionStatus.CLAIMED) return; // not all done
    }
    capPm.setStatus(PlayerMissionStatus.COMPLETED);
    capPm.setProgress(capstone.getObjectiveTarget());
    capPm.setCompletedAt(Instant.now());
    playerMissions.save(capPm);
  }

  // --- claim ------------------------------------------------------------------

  @Transactional
  public Map<String,Object> claim(Long playerId, Long missionId){
    PlayerMission pm = playerMissions.findByPlayerIdAndMissionId(playerId, missionId)
        .orElseThrow(() -> new IllegalArgumentException("Mission not found"));
    if (pm.getStatus() != PlayerMissionStatus.COMPLETED)
      throw new IllegalStateException("This mission cannot be claimed yet");
    Mission m = missions.findById(missionId).orElseThrow();

    applyRewards(playerId, m.getRewards());

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("rewards", m.getRewards());
    if (m.getUnlocksHeroKey() != null){
      Hero unlocked = unlockHero(playerId, m.getUnlocksHeroKey());
      if (unlocked != null) out.put("unlockedHero", heroService.dto(unlocked));
    }
    pm.setStatus(PlayerMissionStatus.CLAIMED);
    playerMissions.save(pm);
    return out;
  }

  private void applyRewards(Long playerId, Map<String,Integer> rewards){
    if (rewards == null || rewards.isEmpty()) return;
    City capital = cities.findByPlayerIdAndCapitalTrue(playerId)
        .orElseGet(() -> { var l = cities.findByPlayerId(playerId); return l.isEmpty() ? null : l.get(0); });
    long heroXp = 0;
    for (var e : rewards.entrySet()){
      int amt = e.getValue() == null ? 0 : e.getValue();
      switch (e.getKey().toLowerCase()){
        case "wood"   -> { if (capital!=null) capital.setWood(capital.getWood()+amt); }
        case "stone"  -> { if (capital!=null) capital.setStone(capital.getStone()+amt); }
        case "silver", "wheat" -> { if (capital!=null) capital.setWheat(capital.getWheat()+amt); }
        case "heroxp" -> heroXp += amt;
        default -> {}
      }
    }
    if (capital != null) cities.save(capital);
    final long xp = heroXp;
    if (xp > 0) heroes.findByOwnerPlayerIdAndHeroKey(playerId, HeroKey.LEO)
        .ifPresent(leo -> heroService.grantXp(playerId, leo.getId(), xp));
  }

  /** Flip a hero to unlocked, creating/stationing it in the capital if needed. */
  @Transactional
  public Hero unlockHero(Long playerId, HeroKey key){
    Long capitalId = cities.findByPlayerIdAndCapitalTrue(playerId).map(City::getId)
        .orElseGet(() -> { var l = cities.findByPlayerId(playerId); return l.isEmpty() ? null : l.get(0).getId(); });
    Hero h = heroes.findByOwnerPlayerIdAndHeroKey(playerId, key).orElse(null);
    if (h == null){
      Race race = key == HeroKey.TITANIA ? Race.FAIRIES : Race.HUMANS;
      String name = key == HeroKey.TITANIA ? "Titania" : "Leo";
      return heroService.create(playerId, key, race, name, true, capitalId);
    }
    if (!h.isUnlocked()){
      h.setUnlocked(true);
      if (h.getStationedCityId() == null && h.getState() == HeroState.IDLE) h.setStationedCityId(capitalId);
      heroes.save(h);
    }
    return h;
  }

  // --- read view --------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<Map<String,Object>> list(Long playerId){
    Map<Long,Mission> cfg = missionConfig();
    List<PlayerMission> pms = playerMissions.findByPlayerId(playerId);
    pms.sort(Comparator.comparingInt(pm -> { Mission m = cfg.get(pm.getMissionId()); return m==null?999:m.getOrderIndex(); }));
    List<Map<String,Object>> out = new ArrayList<>();
    for (PlayerMission pm : pms){
      Mission m = cfg.get(pm.getMissionId());
      if (m == null) continue;
      Map<String,Object> x = new LinkedHashMap<>();
      x.put("missionId", m.getId());
      x.put("order", m.getOrderIndex());
      x.put("title", m.getTitle());
      x.put("description", m.getDescription());
      x.put("objectiveType", m.getObjectiveType().name());
      x.put("target", m.getObjectiveTarget());
      x.put("progress", pm.getProgress());
      x.put("status", pm.getStatus().name());
      x.put("rewards", m.getRewards());
      x.put("unlocksHeroKey", m.getUnlocksHeroKey()==null ? null : m.getUnlocksHeroKey().name());
      out.add(x);
    }
    return out;
  }

  /** {done, total} among the non-capstone STARTER missions — for the Titania unlock teaser. */
  @Transactional(readOnly = true)
  public int[] starterProgress(Long playerId){
    Map<Long,Mission> cfg = missionConfig();
    int done = 0, total = 0;
    for (PlayerMission pm : playerMissions.findByPlayerId(playerId)){
      Mission m = cfg.get(pm.getMissionId());
      if (m == null || !"STARTER".equals(m.getChain()) || m.getObjectiveType()==MissionObjectiveType.CHAIN_COMPLETE) continue;
      total++;
      if (pm.getStatus()==PlayerMissionStatus.COMPLETED || pm.getStatus()==PlayerMissionStatus.CLAIMED) done++;
    }
    return new int[]{done, total};
  }

  private Map<Long,Mission> missionConfig(){
    Map<Long,Mission> m = new HashMap<>();
    for (Mission x : missions.findAll()) m.put(x.getId(), x);
    return m;
  }
}
