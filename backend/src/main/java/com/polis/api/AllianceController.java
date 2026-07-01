package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.AllianceService;
import com.polis.game.AllianceTierService;
import com.polis.repo.PlayerRepo;
import com.polis.domain.Player;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Create / leave a player alliance. */
@RestController
@RequestMapping("/api/alliances")
public class AllianceController {
  private final AllianceService alliances;
  private final AllianceTierService tierProgress;
  private final PlayerRepo players;
  public AllianceController(AllianceService alliances, AllianceTierService tierProgress, PlayerRepo players){
    this.alliances = alliances; this.tierProgress = tierProgress; this.players = players;
  }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record CreateRequest(String tag, String name){}
  public record InviteRequest(String username){}
  public record PostRequest(String body){}
  public record EmblemRequest(String emblem){}

  /** Tier-gate progress for the current player's alliance (or empty gate if none). */
  @GetMapping("/me/tier-progress")
  public Map<String,Object> myTierProgress(){
    Long allianceId = players.findById(me()).map(Player::getAllianceId).orElse(null);
    return tierProgress.view(allianceId);
  }

  @GetMapping("/{id}/tier-progress")
  public Map<String,Object> tierProgress(@PathVariable Long id){ return tierProgress.view(id); }

  @PostMapping("/emblem")
  public Map<String,Object> emblem(@RequestBody EmblemRequest r){
    alliances.setEmblem(me(), r.emblem()); return Map.of("ok", true);
  }

  @PostMapping
  public Map<String,Object> create(@RequestBody CreateRequest r){
    return alliances.create(me(), r.tag(), r.name());
  }

  @PostMapping("/leave")
  public Map<String,Object> leave(){
    alliances.leave(me());
    return Map.of("ok", true);
  }

  /** Full alliance view for the current player (membership, members, forum, or pending invites). */
  @GetMapping("/me")
  public Map<String,Object> myAlliance(){ return alliances.me(me()); }

  @PostMapping("/invite")
  public Map<String,Object> invite(@RequestBody InviteRequest r){
    alliances.invite(me(), r.username()); return Map.of("ok", true);
  }

  @PostMapping("/invites/{allianceId}/accept")
  public Map<String,Object> accept(@PathVariable Long allianceId){
    alliances.acceptInvite(me(), allianceId); return Map.of("ok", true);
  }

  @PostMapping("/invites/{allianceId}/decline")
  public Map<String,Object> decline(@PathVariable Long allianceId){
    alliances.declineInvite(me(), allianceId); return Map.of("ok", true);
  }

  @PostMapping("/forum")
  public Map<String,Object> post(@RequestBody PostRequest r){
    alliances.post(me(), r.body()); return Map.of("ok", true);
  }
}
