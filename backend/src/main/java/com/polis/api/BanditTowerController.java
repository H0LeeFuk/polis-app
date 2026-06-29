package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.BanditTowerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Account-wide Bandit Tower: climb state, the level roadmap, and attacking the current level. */
@RestController
@RequestMapping("/api/players/me/bandit-tower")
public class BanditTowerController {
  private final BanditTowerService tower;
  public BanditTowerController(BanditTowerService tower){ this.tower = tower; }

  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record AttackRequest(Long fromCityId, Map<String,Integer> troops, Long includeHeroId){}

  @GetMapping
  public Map<String,Object> state(){ return tower.state(me()); }

  @GetMapping("/levels")
  public List<Map<String,Object>> levels(){ return tower.levels(me()); }

  @PostMapping("/attack")
  public Map<String,Object> attack(@RequestBody AttackRequest r){
    return tower.attack(me(), r.fromCityId(), r.troops(), r.includeHeroId());
  }
}
