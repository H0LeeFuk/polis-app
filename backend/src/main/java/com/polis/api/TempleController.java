package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.domain.Festival;
import com.polis.game.ProgressionService;
import com.polis.game.TempleService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TempleController {
  private final TempleService temple;
  private final ProgressionService progression;
  public TempleController(TempleService temple, ProgressionService progression){
    this.temple = temple; this.progression = progression;
  }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record FestivalRequest(String festivalType, String fuelType){}

  @PostMapping("/cities/{cityId}/temple/festival")
  public Map<String,Object> festival(@PathVariable Long cityId, @RequestBody FestivalRequest r){
    temple.start(me(), cityId, Festival.Type.valueOf(r.festivalType()), Festival.Fuel.valueOf(r.fuelType()));
    return Map.of("ok", true);
  }

  @GetMapping("/cities/{cityId}/temple")
  public Map<String,Object> temple(@PathVariable Long cityId){ return temple.templeState(me(), cityId); }

  @GetMapping("/players/me/progression")
  public Map<String,Object> progression(){ return progression.progression(me()); }
}
