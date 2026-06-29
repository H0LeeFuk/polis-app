package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.RankingService;
import com.polis.repo.PlayerRepo;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rankings")
public class RankingController {
  private final RankingService ranking; private final PlayerRepo players;
  public RankingController(RankingService ranking, PlayerRepo players){ this.ranking=ranking; this.players=players; }

  @GetMapping
  public List<RankingService.Row> rankings(@RequestParam(defaultValue="points") String type){
    Long worldId = players.findById(SecurityConfig.currentPlayerId()).orElseThrow().getWorldId();
    return switch (type){
      case "combat"         -> ranking.byCombat(worldId);
      case "alliances"      -> ranking.alliancesByCombat(worldId);
      case "alliancePoints" -> ranking.alliancesBy(worldId, true);
      default               -> ranking.byPoints(worldId);
    };
  }
}
