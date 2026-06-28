package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.CityRepo;
import com.polis.repo.HeroRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions a player's heroes (Leo unlocked, Celine locked) and seeds the starter mission chain.
 * Called at registration and by {@link com.polis.game.AccountBackfillRunner} for pre-existing players.
 * Idempotent: only creates what is missing.
 */
@Service
public class AccountSetupService {
  private final HeroService heroes;
  private final HeroRepo heroRepo;
  private final MissionService missions;
  private final CityRepo cities;

  public AccountSetupService(HeroService heroes, HeroRepo heroRepo, MissionService missions, CityRepo cities){
    this.heroes = heroes; this.heroRepo = heroRepo; this.missions = missions; this.cities = cities;
  }

  @Transactional
  public void setup(Long playerId){
    Long capitalId = cities.findByPlayerIdAndCapitalTrue(playerId).map(City::getId)
        .orElseGet(() -> { var l = cities.findByPlayerId(playerId); return l.isEmpty() ? null : l.get(0).getId(); });

    if (heroRepo.findByOwnerPlayerIdAndHeroKey(playerId, HeroKey.LEO).isEmpty())
      heroes.create(playerId, HeroKey.LEO, Race.HUMANS, "Leo", true, capitalId);
    if (heroRepo.findByOwnerPlayerIdAndHeroKey(playerId, HeroKey.CELINE).isEmpty())
      heroes.create(playerId, HeroKey.CELINE, Race.FAIRIES, "Celine", false, null);

    missions.seedForPlayer(playerId);
  }
}
