package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.ColossusService;
import com.polis.repo.PlayerRepo;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Daily roaming PvE world bosses: live state, per-alliance damage leaderboard, and attacks. */
@RestController
@RequestMapping("/api/world/colossi")
public class ColossusController {
  private final ColossusService colossi;
  private final PlayerRepo players;
  public ColossusController(ColossusService colossi, PlayerRepo players){ this.colossi = colossi; this.players = players; }

  private Long me(){ return SecurityConfig.currentPlayerId(); }
  private Long worldId(){ return players.findById(me()).orElseThrow().getWorldId(); }

  public record AttackReq(Long fromCityId, Map<String,Integer> troops, Long includeHeroId){}

  @GetMapping
  public List<Map<String,Object>> active(){ return colossi.listActive(worldId(), me()); }

  @GetMapping("/{id}")
  public Map<String,Object> detail(@PathVariable Long id){ return colossi.detail(id, me()); }

  @GetMapping("/{id}/leaderboard")
  public List<Map<String,Object>> leaderboard(@PathVariable Long id){ return colossi.leaderboard(id); }

  @PostMapping("/{id}/attack")
  public Map<String,Object> attack(@PathVariable Long id, @RequestBody AttackReq r){
    return colossi.attack(me(), id, r.fromCityId(), r.troops(), r.includeHeroId());
  }

  /** DEV: spawn today's Colossus immediately (the 21:00 scheduler does this in production). */
  @PostMapping("/spawn-now")
  public Map<String,Object> spawnNow(){ colossi.spawnDaily(Instant.now()); return Map.of("ok", true); }
}
