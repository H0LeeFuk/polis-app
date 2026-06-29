package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.WonderService;
import com.polis.repo.PlayerRepo;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Endgame: world phase, the three Wonders, alliance leaderboard, and capture/invest actions. */
@RestController
@RequestMapping("/api/world")
public class WonderController {
  private final WonderService wonders;
  private final PlayerRepo players;
  public WonderController(WonderService wonders, PlayerRepo players){ this.wonders = wonders; this.players = players; }

  private Long me(){ return SecurityConfig.currentPlayerId(); }
  private Long worldId(){ return players.findById(me()).orElseThrow().getWorldId(); }

  public record TroopMove(Long cityId, Map<String,Integer> troops, Long heroId){}
  public record Withdraw(Map<String,Integer> troops){}
  public record Invest(Long cityId, long each){}

  @GetMapping("/state")
  public Map<String,Object> state(){ return wonders.worldStateDto(worldId(), Instant.now()); }

  @GetMapping("/leaderboard")
  public List<Map<String,Object>> leaderboard(){ return wonders.leaderboard(worldId()); }

  @PostMapping("/wonders/{id}/occupy")
  public Map<String,Object> occupy(@PathVariable Long id, @RequestBody TroopMove r){
    wonders.occupy(me(), id, r.cityId(), r.troops(), r.heroId()); return ok();
  }

  @PostMapping("/wonders/{id}/attack")
  public Map<String,Object> attack(@PathVariable Long id, @RequestBody TroopMove r){
    wonders.attack(me(), id, r.cityId(), r.troops(), r.heroId()); return ok();
  }

  @PostMapping("/wonders/{id}/withdraw")
  public Map<String,Object> withdraw(@PathVariable Long id, @RequestBody(required=false) Withdraw r){
    wonders.withdraw(me(), id, r == null ? null : r.troops()); return ok();
  }

  @PostMapping("/wonders/{id}/invest")
  public Map<String,Object> invest(@PathVariable Long id, @RequestBody Invest r){
    return wonders.invest(me(), id, r.cityId(), r.each());
  }

  /** DEV: jump the world straight to endgame so Wonders become contestable now. */
  @PostMapping("/force-endgame")
  public Map<String,Object> forceEndgame(){ wonders.forceEndgame(worldId()); return ok(); }

  private Map<String,Object> ok(){ return Map.of("ok", true); }
}
