package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.BanditCampService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Per-island bandit camp: read state and attack the current level. */
@RestController
public class BanditCampController {
  private final BanditCampService camps;
  public BanditCampController(BanditCampService camps){ this.camps = camps; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record AttackRequest(Long cityId, Map<String,Integer> troops){}

  @GetMapping("/api/islands/{islandId}/bandit-camp")
  public Map<String,Object> camp(@PathVariable Long islandId){
    return camps.dto(islandId, me());
  }

  @PostMapping("/api/islands/{islandId}/bandit-camp/attack")
  public Map<String,Object> attack(@PathVariable Long islandId, @RequestBody AttackRequest r){
    return camps.attack(me(), islandId, r.cityId(), r.troops());
  }
}
