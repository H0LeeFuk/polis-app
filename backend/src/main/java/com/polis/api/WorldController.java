package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/world")
public class WorldController {
  private final PlayerRepo players; private final IslandRepo islands; private final CityRepo cities; private final AllianceRepo alliances;
  public WorldController(PlayerRepo players, IslandRepo islands, CityRepo cities, AllianceRepo alliances){
    this.players=players; this.islands=islands; this.cities=cities; this.alliances=alliances;
  }

  @GetMapping
  public Map<String,Object> world(){
    Long me = SecurityConfig.currentPlayerId();
    Long worldId = players.findById(me).orElseThrow().getWorldId();
    Map<Long,String> allyTag = new HashMap<>();
    alliances.findByWorldId(worldId).forEach(a->allyTag.put(a.getId(), a.getName()));
    Map<Long,Long> playerAlliance = new HashMap<>();
    players.findByWorldId(worldId).forEach(p->{ if(p.getAllianceId()!=null) playerAlliance.put(p.getId(), p.getAllianceId()); });

    List<Map<String,Object>> isl = new ArrayList<>();
    for (Island i : islands.findByWorldId(worldId)){
      Map<String,Object> mi = new LinkedHashMap<>();
      mi.put("id",i.getId()); mi.put("name",i.getName()); mi.put("px",i.getPx()); mi.put("py",i.getPy());
      List<Map<String,Object>> slots = new ArrayList<>();
      for (City c : cities.findByIslandId(i.getId())){
        Map<String,Object> mc = new LinkedHashMap<>();
        mc.put("id",c.getId()); mc.put("slot",c.getSlot()); mc.put("name",c.getName()); mc.put("points",c.getPoints());
        mc.put("power",(long)c.getPower());
        String faction = c.getPlayerId()==null ? "barbarian"
            : Objects.equals(c.getPlayerId(), me) ? "self"
            : sameAlliance(playerAlliance.get(me), playerAlliance.get(c.getPlayerId())) ? "ally" : "enemy";
        mc.put("faction", faction);
        slots.add(mc);
      }
      mi.put("cities", slots);
      isl.add(mi);
    }
    return Map.of("islands", isl);
  }

  private boolean sameAlliance(Long a, Long b){ return a!=null && a.equals(b); }
}
