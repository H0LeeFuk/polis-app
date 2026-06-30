package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.TroopDetailService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Troop Detail panel: this city's troops stationed abroad, and foreign troops garrisoned here. */
@RestController
@RequestMapping("/api/cities/{cityId}")
public class TroopDetailController {
  private final TroopDetailService troops;
  public TroopDetailController(TroopDetailService troops){ this.troops = troops; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record RecallRequest(String locationType, Long locationId){}
  public record DismissRequest(Long ownerPlayerId){}

  @GetMapping("/troops-abroad")
  public List<Map<String,Object>> abroad(@PathVariable Long cityId){ return troops.troopsAbroad(me(), cityId); }

  @GetMapping("/foreign-troops")
  public List<Map<String,Object>> foreign(@PathVariable Long cityId){ return troops.foreignTroops(me(), cityId); }

  @PostMapping("/recall-abroad")
  public Map<String,Object> recall(@PathVariable Long cityId, @RequestBody RecallRequest r){
    troops.recallAbroad(me(), cityId, r.locationType(), r.locationId());
    return Map.of("ok", true);
  }

  @PostMapping("/dismiss-foreign")
  public Map<String,Object> dismiss(@PathVariable Long cityId, @RequestBody DismissRequest r){
    troops.dismissForeign(me(), cityId, r.ownerPlayerId());
    return Map.of("ok", true);
  }
}
