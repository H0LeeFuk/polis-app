package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.domain.City;
import com.polis.game.SettleService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** City founding: island slots, sending the hero to settle, and choosing a race on arrival. */
@RestController
public class SettleController {
  private final SettleService settle;
  public SettleController(SettleService settle){ this.settle = settle; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record SettleRequest(Long fromCityId, Long heroId){}
  public record FoundRequest(String race, String cityName, Long heroReturnCityId){}

  /** All 12 slots of an island with occupancy + whether the player may settle each empty one. */
  @GetMapping("/api/islands/{islandId}/slots")
  public Map<String,Object> slots(@PathVariable Long islandId){
    return settle.slots(me(), islandId);
  }

  /** Travel-time preview for the hero's march to this island (before dispatching). */
  @GetMapping("/api/islands/{islandId}/settle/preview")
  public Map<String,Object> settlePreview(@PathVariable Long islandId, @RequestParam Long fromCityId,
                                          @RequestParam(required=false) Long heroId){
    return settle.settlePreview(me(), islandId, fromCityId, heroId);
  }

  /** Send the hero to an empty slot to begin founding (creates a SETTLE movement). */
  @PostMapping("/api/islands/{islandId}/slots/{slotIndex}/settle")
  public Map<String,Object> settle(@PathVariable Long islandId, @PathVariable int slotIndex,
                                   @RequestBody SettleRequest r){
    var m = settle.settle(me(), islandId, slotIndex, r.fromCityId(), r.heroId());
    return Map.of("ok", true, "movementId", m.getId(),
        "arriveAt", m.getArriveAt()==null ? null : m.getArriveAt().toString());
  }

  /** Choose a race and found the city once the hero has arrived. */
  @PostMapping("/api/islands/{islandId}/slots/{slotIndex}/found-city")
  public Map<String,Object> found(@PathVariable Long islandId, @PathVariable int slotIndex,
                                  @RequestBody FoundRequest r){
    City c = settle.foundCity(me(), islandId, slotIndex, r.race(), r.cityName(), r.heroReturnCityId());
    if (c == null)
      return Map.of("ok", false, "message", "That slot was taken while you decided — your hero is returning home");
    return Map.of("ok", true, "cityId", c.getId());
  }

  /** Any in-progress SETTLE march or a founding awaiting a race choice. */
  @GetMapping("/api/players/me/founding-status")
  public Map<String,Object> foundingStatus(){
    return settle.foundingStatus(me());
  }
}
