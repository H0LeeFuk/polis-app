package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.domain.*;
import com.polis.game.BuildService;
import com.polis.game.MovementService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/cities/{cityId}")
public class CityController {
  private final BuildService build;
  private final MovementService movements;
  public CityController(BuildService build, MovementService movements){ this.build = build; this.movements = movements; }
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
  @PostMapping("/finish/{jobId}")
  public Map<String,Object> finish(@PathVariable Long cityId, @PathVariable Long jobId){
    build.finishWithGold(me(), cityId, jobId); return ok();
  }
  @PostMapping("/colonize")
  public Map<String,Object> colonize(@PathVariable Long cityId, @RequestBody ColonizeRequest r){
    build.colonize(me(), cityId, r.islandId(), r.slot()); return ok();
  }
  // /attack is the spec name; /raid stays as an alias for existing callers. Both return the
  // created movement (incl. arriveAt) so the UI can show the ETA right after dispatching.
  @PostMapping({"/attack", "/raid"})
  public MovementDTO attack(@PathVariable Long cityId, @RequestBody RaidRequest r){
    Long me = me();
    Movement m = build.raid(me, cityId, r.targetCityId(), r.units());
    return movements.dto(m, me);
  }
  private Map<String,Object> ok(){ return Map.of("ok", true); }
}
