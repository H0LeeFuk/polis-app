package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.IslandBossService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Resource-island guardian boss: read state + attack it (instant PvE, drops a rare relic). */
@RestController
public class IslandBossController {
  private final IslandBossService boss;
  public IslandBossController(IslandBossService boss){ this.boss = boss; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record AttackRequest(Long cityId, Map<String,Integer> troops, Long heroId){}

  @GetMapping("/api/islands/{islandId}/boss")
  public Map<String,Object> get(@PathVariable Long islandId){ return boss.dto(islandId); }

  @PostMapping("/api/islands/{islandId}/boss/attack")
  public Map<String,Object> attack(@PathVariable Long islandId, @RequestBody AttackRequest r){
    return boss.attack(me(), islandId, r.cityId(), r.troops(), r.heroId());
  }
}
