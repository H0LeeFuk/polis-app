package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.LibraryService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Per-city Library: research tree, start research, re-spec, active bonuses. */
@RestController
@RequestMapping("/api/cities/{cityId}/library")
public class LibraryController {
  private final LibraryService library;
  public LibraryController(LibraryService library){ this.library = library; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record ResearchRequest(String researchId){}

  @GetMapping
  public Map<String,Object> get(@PathVariable Long cityId){ return library.library(me(), cityId); }

  @PostMapping("/research")
  public Map<String,Object> research(@PathVariable Long cityId, @RequestBody ResearchRequest r){
    library.startResearch(me(), cityId, r.researchId());
    return Map.of("ok", true);
  }

  @PostMapping("/respec")
  public Map<String,Object> respec(@PathVariable Long cityId){
    library.respec(me(), cityId);
    return Map.of("ok", true);
  }

  @GetMapping("/active-bonuses")
  public Map<String,Object> activeBonuses(@PathVariable Long cityId){ return library.activeBonuses(me(), cityId); }

  /** Bastion "City Guard": summon farmer-militia into the garrison (on a cooldown). */
  @PostMapping("/call-guard")
  public Map<String,Object> callGuard(@PathVariable Long cityId){ return library.callGuard(me(), cityId); }
}
