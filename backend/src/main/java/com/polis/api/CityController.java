package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.domain.*;
import com.polis.game.BuildService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/cities/{cityId}")
public class CityController {
  private final BuildService build;
  public CityController(BuildService build){ this.build = build; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record BuildRequest(String buildingType){}
  public record TrainRequest(String unitType, int count){}
  public record ResearchRequest(String researchType){}
  public record RenameRequest(String name){}
  public record ColonizeRequest(Long islandId, int slot){}
  public record RaidRequest(Long targetCityId, Map<String,Integer> units){}

  @PostMapping("/build")
  public Map<String,Object> build(@PathVariable Long cityId, @RequestBody BuildRequest r){
    build.build(me(), cityId, BuildingType.valueOf(r.buildingType())); return ok();
  }
  @PostMapping("/train")
  public Map<String,Object> train(@PathVariable Long cityId, @RequestBody TrainRequest r){
    build.train(me(), cityId, UnitType.valueOf(r.unitType()), r.count()); return ok();
  }
  @PostMapping("/research")
  public Map<String,Object> research(@PathVariable Long cityId, @RequestBody ResearchRequest r){
    build.research(me(), cityId, ResearchType.valueOf(r.researchType())); return ok();
  }
  @PostMapping("/rename")
  public Map<String,Object> rename(@PathVariable Long cityId, @RequestBody RenameRequest r){
    build.rename(me(), cityId, r.name()); return ok();
  }
  @PostMapping("/cancel/{jobId}")
  public Map<String,Object> cancel(@PathVariable Long cityId, @PathVariable Long jobId){
    build.cancel(me(), cityId, jobId); return ok();
  }
  @PostMapping("/colonize")
  public Map<String,Object> colonize(@PathVariable Long cityId, @RequestBody ColonizeRequest r){
    build.colonize(me(), cityId, r.islandId(), r.slot()); return ok();
  }
  @PostMapping("/raid")
  public Map<String,Object> raid(@PathVariable Long cityId, @RequestBody RaidRequest r){
    build.raid(me(), cityId, r.targetCityId(), r.units()); return ok();
  }
  private Map<String,Object> ok(){ return Map.of("ok", true); }
}
