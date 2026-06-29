package com.polis.auth;

import com.polis.domain.*;
import com.polis.game.CityFactory;
import com.polis.game.AccountSetupService;
import com.polis.repo.*;
import com.polis.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AuthService {
  private final PlayerRepo players; private final WorldRepo worlds; private final IslandRepo islands;
  private final CityRepo cities; private final CityFactory cityFactory;
  private final AccountSetupService accountSetup;
  private final PasswordEncoder encoder; private final JwtService jwt;

  public AuthService(PlayerRepo players, WorldRepo worlds, IslandRepo islands, CityRepo cities,
                     CityFactory cityFactory, AccountSetupService accountSetup, PasswordEncoder encoder, JwtService jwt){
    this.players=players; this.worlds=worlds; this.islands=islands; this.cities=cities;
    this.cityFactory=cityFactory; this.accountSetup=accountSetup; this.encoder=encoder; this.jwt=jwt;
  }

  @Transactional
  public String register(String username, String email, String password){
    if (players.existsByUsername(username)) throw new IllegalStateException("Username taken");
    if (email!=null && !email.isBlank() && players.existsByEmail(email)) throw new IllegalStateException("Email in use");
    World world = worlds.findAll().stream().findFirst().orElseThrow(() -> new IllegalStateException("World not seeded yet"));

    Player p = new Player();
    p.setUsername(username); p.setEmail(email); p.setPasswordHash(encoder.encode(password));
    p.setWorldId(world.getId());
    p = players.save(p);

    placeCapital(p);
    // provision Leo (unlocked) + Titania (locked) and seed the starter mission chain
    accountSetup.setup(p.getId());
    return jwt.issue(p.getId(), p.getUsername());
  }

  @Transactional
  public String login(String username, String password){
    Player p = players.findByUsername(username).orElseThrow(() -> new IllegalStateException("Invalid credentials"));
    if (p.isNpc() || !encoder.matches(password, p.getPasswordHash()))
      throw new IllegalStateException("Invalid credentials");
    // Lost-everything respawn: a player who has no cities left (e.g. conquered out) re-spawns with a
    // fresh capital on a populated tier-3 island, same placement rule as a brand-new player.
    if (cities.countByPlayerId(p.getId()) == 0) placeCapital(p);
    return jwt.issue(p.getId(), p.getUsername());
  }

  /**
   * Inward-growth spawn that CONCENTRATES players: new players (and respawns) land on the outer rim
   * (tier 3) and the world fills toward the core. Within a tier we finish the most-populated island
   * that still has room before opening a fresh one — so an island fills completely before the next
   * is started, and players cluster on already-populated islands. Resource/wonder islands (tier 0)
   * are skipped. Falls back to any free plot for worlds without tier data.
   */
  private long[] firstFreeSlot(Long worldId){
    List<Island> isls = new ArrayList<>(islands.findByWorldId(worldId));
    isls.sort(Comparator.comparingLong(Island::getId));   // deterministic order for empty-island pick
    int SLOTS = com.polis.game.GameRules.SLOTS_PER_ISLAND;
    for (int tier = 3; tier >= 1; tier--){
      Island chosen = null; int chosenFree = Integer.MAX_VALUE; boolean chosenPopulated = false;
      for (Island i : isls){
        if (i.getTier() != tier || i.isResource()) continue;
        int occupied = cities.findByIslandId(i.getId()).size();
        int free = SLOTS - occupied;
        if (free <= 0) continue;                                   // full — skip
        boolean populated = occupied > 0;
        // prefer a populated island; among equals take the fewest free (closest to full); only fall
        // back to an empty island when no populated one in this tier has room
        boolean better = chosen == null
            || (populated && !chosenPopulated)
            || (populated == chosenPopulated && free < chosenFree);
        if (better){ chosen = i; chosenFree = free; chosenPopulated = populated; }
      }
      if (chosen != null)
        for (int s=0; s<SLOTS; s++)
          if (cities.findByIslandIdAndSlot(chosen.getId(), s).isEmpty()) return new long[]{chosen.getId(), s};
    }
    // fallback: any island with a free plot (covers worlds without tier data)
    for (Island i : isls)
      for (int s=0; s<SLOTS; s++)
        if (cities.findByIslandIdAndSlot(i.getId(), s).isEmpty()) return new long[]{i.getId(), s};
    throw new IllegalStateException("No free city plots remain in this world");
  }

  /** Give a city-less account a fresh capital on a tier-3 island (new spawn / lost-everything respawn). */
  private void placeCapital(Player p){
    long[] slot = firstFreeSlot(p.getWorldId());
    cityFactory.createPlayerCity(p.getWorldId(), p.getId(), slot[0], (int)slot[1], p.getUsername() + "’s Polis", true);
  }
}
