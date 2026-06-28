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
    Map<Long,Player> playerById = new HashMap<>();
    players.findByWorldId(worldId).forEach(p->{
      playerById.put(p.getId(), p);
      if(p.getAllianceId()!=null) playerAlliance.put(p.getId(), p.getAllianceId());
    });

    List<Map<String,Object>> isl = new ArrayList<>();
    Set<Long> ownerIds = new HashSet<>();
    for (Island i : islands.findByWorldId(worldId)){
      Map<String,Object> mi = new LinkedHashMap<>();
      mi.put("id",i.getId()); mi.put("name",i.getName()); mi.put("px",i.getPx()); mi.put("py",i.getPy());
      mi.put("resource", i.isResource());
      List<Map<String,Object>> slots = new ArrayList<>();
      for (City c : cities.findByIslandId(i.getId())){
        Map<String,Object> mc = new LinkedHashMap<>();
        mc.put("id",c.getId()); mc.put("slot",c.getSlot()); mc.put("name",c.getName()); mc.put("points",c.getPoints());
        mc.put("power",(long)c.getPower());
        mc.put("race", c.getRace()==null ? null : c.getRace().name());
        Long pid = c.getPlayerId();
        mc.put("playerId", pid);
        mc.put("owner", pid==null ? "Barbarians" : playerById.containsKey(pid) ? playerById.get(pid).getUsername() : "Unknown");
        if (pid!=null) ownerIds.add(pid);
        String faction = pid==null ? "barbarian"
            : Objects.equals(pid, me) ? "self"
            : sameAlliance(playerAlliance.get(me), playerAlliance.get(pid)) ? "ally" : "enemy";
        mc.put("faction", faction);
        slots.add(mc);
      }
      mi.put("cities", slots);
      isl.add(mi);
    }

    List<Map<String,Object>> playerList = new ArrayList<>();
    for (Long pid : ownerIds){
      Player p = playerById.get(pid); if (p==null) continue;
      Map<String,Object> mp = new LinkedHashMap<>();
      mp.put("id",p.getId()); mp.put("name",p.getUsername());
      mp.put("level",p.getLevel()); mp.put("combatPoints",p.getCombatPoints());
      playerList.add(mp);
    }
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("islands", isl); out.put("players", playerList);
    return out;
  }

  private boolean sameAlliance(Long a, Long b){ return a!=null && a.equals(b); }
}
