package com.polis.game;

import com.polis.domain.Player;
import com.polis.repo.PlayerRepo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot migration helper: ensures every human player has the two heroes (existing single
 * hero already mapped to LEO by V10) plus seeded starter missions. Runs after world seeding.
 *
 * <p>Pre-existing players re-enter the starter chain (their old hero becomes Leo, Titania starts
 * locked) — acceptable for this dev/migration; document if grandfathering established players.
 */
@Component
@Order(20)
public class AccountBackfillRunner implements ApplicationRunner {
  private final PlayerRepo players;
  private final AccountSetupService setup;

  public AccountBackfillRunner(PlayerRepo players, AccountSetupService setup){
    this.players = players; this.setup = setup;
  }

  @Override @Transactional
  public void run(ApplicationArguments args){
    for (Player p : players.findAll()){
      if (p.isNpc()) continue;
      setup.setup(p.getId());
    }
  }
}
