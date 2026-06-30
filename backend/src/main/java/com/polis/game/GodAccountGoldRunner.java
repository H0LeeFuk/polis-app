package com.polis.game;

import com.polis.repo.PlayerRepo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot dev grant: tops the god account's gold up to {@link #GOLD} on startup so it
 * always has premium currency for rush testing. Only raises (never lowers) the balance.
 */
@Component
@Order(30)
public class GodAccountGoldRunner implements ApplicationRunner {
  private static final String GOD_ACCOUNT = "bruno";
  private static final int GOLD = 2_000_000_000;   // effectively infinite for rush testing

  private final PlayerRepo players;

  public GodAccountGoldRunner(PlayerRepo players){ this.players = players; }

  @Override @Transactional
  public void run(ApplicationArguments args){
    players.findByUsernameIgnoreCase(GOD_ACCOUNT).ifPresent(p -> {
      if (p.getGold() < GOLD){ p.setGold(GOLD); players.save(p); }
    });
  }
}
