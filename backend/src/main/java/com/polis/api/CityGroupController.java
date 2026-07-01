package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.CityGroupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Per-player City Groups: organize owned cities into named, icon-tagged groups (UI/navigation only). */
@RestController
@RequestMapping("/api/players/me")
public class CityGroupController {
  private final CityGroupService service;
  public CityGroupController(CityGroupService service){ this.service = service; }

  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record GroupBody(String name, String icon, Integer sortOrder){}
  public record CityIdsBody(List<Long> cityIds){}

  @GetMapping("/city-groups")
  public Map<String,Object> list(){ return service.list(me()); }

  @PostMapping("/city-groups")
  public Map<String,Object> create(@RequestBody GroupBody b){ return service.create(me(), b.name(), b.icon()); }

  @PatchMapping("/city-groups/{id}")
  public Map<String,Object> edit(@PathVariable Long id, @RequestBody GroupBody b){
    service.edit(me(), id, b.name(), b.icon(), b.sortOrder());
    return Map.of("ok", true);
  }

  @DeleteMapping("/city-groups/{id}")
  public Map<String,Object> delete(@PathVariable Long id){ service.delete(me(), id); return Map.of("ok", true); }

  @PostMapping("/city-groups/{id}/cities")
  public Map<String,Object> addCities(@PathVariable Long id, @RequestBody CityIdsBody b){
    service.addCities(me(), id, b.cityIds());
    return Map.of("ok", true);
  }

  @DeleteMapping("/city-groups/{id}/cities")
  public Map<String,Object> removeCities(@PathVariable Long id, @RequestBody CityIdsBody b){
    service.removeCities(me(), id, b.cityIds());
    return Map.of("ok", true);
  }

  @GetMapping("/cities-overview")
  public List<Map<String,Object>> citiesOverview(){ return service.citiesOverview(me()); }
}
