package com.polis.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polis.config.SecurityConfig;
import com.polis.game.MovementService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Read endpoints for the Troop Movements feature (city panel, global overview, attack preview). */
@RestController
public class MovementController {
  private final MovementService movements;
  private final ObjectMapper mapper;

  public MovementController(MovementService movements, ObjectMapper mapper){
    this.movements = movements; this.mapper = mapper;
  }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  /** All movements involving this city: outgoing, returning, and incoming hostile. */
  @GetMapping("/api/cities/{cityId}/movements")
  public List<MovementDTO> cityMovements(@PathVariable Long cityId){
    return movements.cityMovements(me(), cityId);
  }

  /** Live travel-time preview while the player picks troops — does NOT create a movement. */
  @GetMapping("/api/cities/{originCityId}/attack/preview")
  public Map<String,Object> attackPreview(@PathVariable Long originCityId,
                                           @RequestParam Long targetCityId,
                                           @RequestParam String units){
    return movements.preview(me(), originCityId, targetCityId, parseUnits(units));
  }

  /** Every movement across all of the player's cities, grouped with a summary bar. */
  @GetMapping("/api/players/me/movements")
  public Map<String,Object> myMovements(){
    return movements.playerMovements(me());
  }

  private Map<String,Integer> parseUnits(String json){
    try {
      Map<String,Integer> m = mapper.readValue(json, new TypeReference<Map<String,Integer>>(){});
      return m == null ? Map.of() : m;
    } catch (Exception e){
      throw new IllegalArgumentException("Invalid units payload");
    }
  }
}
