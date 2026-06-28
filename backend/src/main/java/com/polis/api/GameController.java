package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.GameService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameController {
  private final GameService game;
  public GameController(GameService game){ this.game = game; }

  @GetMapping("/state")
  public Map<String,Object> state(@RequestParam(required=false) Long cityId){
    return game.state(SecurityConfig.currentPlayerId(), cityId);
  }
}
