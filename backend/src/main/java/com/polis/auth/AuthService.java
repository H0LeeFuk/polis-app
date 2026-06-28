package com.polis.auth;

import com.polis.domain.*;
import com.polis.game.CityFactory;
import com.polis.repo.*;
import com.polis.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {
  private final PlayerRepo players; private final WorldRepo worlds; private final IslandRepo islands;
  private final CityRepo cities; private final CityFactory cityFactory;
  private final PasswordEncoder encoder; private final JwtService jwt;

  public AuthService(PlayerRepo players, WorldRepo worlds, IslandRepo islands, CityRepo cities,
                     CityFactory cityFactory, PasswordEncoder encoder, JwtService jwt){
    this.players=players; this.worlds=worlds; this.islands=islands; this.cities=cities;
    this.cityFactory=cityFactory; this.encoder=encoder; this.jwt=jwt;
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

    long[] slot = firstFreeSlot(world.getId());
    cityFactory.createPlayerCity(world.getId(), p.getId(), slot[0], (int)slot[1], username + "’s Polis", true);
    return jwt.issue(p.getId(), p.getUsername());
  }

  @Transactional
  public String login(String username, String password){
    Player p = players.findByUsername(username).orElseThrow(() -> new IllegalStateException("Invalid credentials"));
    if (p.isNpc() || !encoder.matches(password, p.getPasswordHash()))
      throw new IllegalStateException("Invalid credentials");
    return jwt.issue(p.getId(), p.getUsername());
  }

  private long[] firstFreeSlot(Long worldId){
    List<Island> isls = islands.findByWorldId(worldId);
    for (Island i : isls)
      for (int s=0; s<10; s++)
        if (cities.findByIslandIdAndSlot(i.getId(), s).isEmpty()) return new long[]{i.getId(), s};
    throw new IllegalStateException("No free city plots remain in this world");
  }
}
