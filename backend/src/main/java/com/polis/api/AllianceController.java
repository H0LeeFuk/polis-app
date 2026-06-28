package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.AllianceService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Create / leave a player alliance. */
@RestController
@RequestMapping("/api/alliances")
public class AllianceController {
  private final AllianceService alliances;
  public AllianceController(AllianceService alliances){ this.alliances = alliances; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record CreateRequest(String tag, String name){}

  @PostMapping
  public Map<String,Object> create(@RequestBody CreateRequest r){
    return alliances.create(me(), r.tag(), r.name());
  }

  @PostMapping("/leave")
  public Map<String,Object> leave(){
    alliances.leave(me());
    return Map.of("ok", true);
  }
}
