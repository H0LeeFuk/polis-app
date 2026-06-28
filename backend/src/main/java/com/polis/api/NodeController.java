package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.NodeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Contested resource nodes: read state, occupy/reinforce/withdraw, and attack. */
@RestController
public class NodeController {
  private final NodeService nodes;
  public NodeController(NodeService nodes){ this.nodes = nodes; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record TroopMove(Long cityId, Map<String,Integer> troops, Long heroId){}
  public record Withdraw(Map<String,Integer> troops){}

  @GetMapping("/api/islands/{islandId}/nodes")
  public List<Map<String,Object>> islandNodes(@PathVariable Long islandId){ return nodes.islandNodes(islandId); }

  @GetMapping("/api/nodes/{nodeId}")
  public Map<String,Object> node(@PathVariable Long nodeId){ return nodes.node(nodeId); }

  @GetMapping("/api/players/me/nodes")
  public List<Map<String,Object>> myNodes(){ return nodes.myNodes(me()); }

  @PostMapping("/api/nodes/{nodeId}/occupy")
  public Map<String,Object> occupy(@PathVariable Long nodeId, @RequestBody TroopMove r){
    nodes.occupy(me(), nodeId, r.cityId(), r.troops(), r.heroId()); return ok();
  }

  @PostMapping("/api/nodes/{nodeId}/reinforce")
  public Map<String,Object> reinforce(@PathVariable Long nodeId, @RequestBody TroopMove r){
    nodes.reinforce(me(), nodeId, r.cityId(), r.troops()); return ok();
  }

  @PostMapping("/api/nodes/{nodeId}/withdraw")
  public Map<String,Object> withdraw(@PathVariable Long nodeId, @RequestBody(required=false) Withdraw r){
    nodes.withdraw(me(), nodeId, r == null ? null : r.troops()); return ok();
  }

  @PostMapping("/api/nodes/{nodeId}/attack")
  public Map<String,Object> attack(@PathVariable Long nodeId, @RequestBody TroopMove r){
    nodes.attack(me(), nodeId, r.cityId(), r.troops(), r.heroId()); return ok();
  }

  private Map<String,Object> ok(){ return Map.of("ok", true); }
}
