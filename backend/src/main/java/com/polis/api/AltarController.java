package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.domain.Festival;
import com.polis.game.ProgressionService;
import com.polis.game.AltarService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AltarController {
  private final AltarService altar;
  private final ProgressionService progression;
  public AltarController(AltarService altar, ProgressionService progression){
    this.altar = altar; this.progression = progression;
  }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record FestivalRequest(String festivalType, String fuelType){}

  @PostMapping("/cities/{cityId}/altar/festival")
  public Map<String,Object> festival(@PathVariable Long cityId, @RequestBody FestivalRequest r){
    altar.start(me(), cityId, Festival.Type.valueOf(r.festivalType()), Festival.Fuel.valueOf(r.fuelType()));
    return Map.of("ok", true);
  }

  @GetMapping("/cities/{cityId}/altar")
  public Map<String,Object> altar(@PathVariable Long cityId){ return altar.altarState(me(), cityId); }

  @GetMapping("/players/me/progression")
  public Map<String,Object> progression(){ return progression.progression(me()); }
}
