package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.MissionService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Player missions: the starter chain progress + reward claiming. */
@RestController
@RequestMapping("/api/players/me/missions")
public class MissionController {
  private final MissionService missions;
  public MissionController(MissionService missions){ this.missions = missions; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  /** Chain-ordered missions with status + progress, plus starter-progress for the Titania teaser. */
  @GetMapping
  public Map<String,Object> list(){
    Long me = me();
    int[] sp = missions.starterProgress(me);
    return Map.of("missions", missions.list(me), "starterDone", sp[0], "starterTotal", sp[1]);
  }

  @PostMapping("/{missionId}/claim")
  public Map<String,Object> claim(@PathVariable Long missionId){
    return missions.claim(me(), missionId);
  }
}
