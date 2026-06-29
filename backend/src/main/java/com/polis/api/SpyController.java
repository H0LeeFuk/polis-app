package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.SpyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Espionage: Watchtower view, launching spy missions, and reading intel / spy alerts. */
@RestController
@RequestMapping("/api")
public class SpyController {
  private final SpyService spy;
  public SpyController(SpyService spy){ this.spy = spy; }

  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record SpyReq(Long targetCityId){}

  @GetMapping("/cities/{cityId}/watchtower")
  public Map<String,Object> watchtower(@PathVariable Long cityId){ return spy.watchtower(me(), cityId); }

  @PostMapping("/cities/{cityId}/spy")
  public Map<String,Object> launch(@PathVariable Long cityId, @RequestBody SpyReq r){
    return spy.spy(me(), cityId, r.targetCityId());
  }

  @GetMapping("/players/me/spy-reports")
  public List<Map<String,Object>> reports(){ return spy.myReports(me()); }

  @GetMapping("/players/me/spy-alerts")
  public List<Map<String,Object>> alerts(){ return spy.myAlerts(me()); }

  @GetMapping("/players/me/intel")
  public Map<String,Object> intel(@RequestParam Long targetCityId){
    Map<String,Object> i = spy.latestIntel(me(), targetCityId);
    return i == null ? Map.of("hasIntel", false) : Map.of("hasIntel", true, "intel", i);
  }
}
