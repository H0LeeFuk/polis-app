package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.HeroService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Account heroes (Leo + Celine): list/read, attribute spend, stationing, skills, equipment. */
@RestController
@RequestMapping("/api/players/me")
public class HeroController {
  private final HeroService heroes;
  public HeroController(HeroService heroes){ this.heroes = heroes; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record AttributesRequest(int leadership, int cunning, int valor){}
  public record StationRequest(Long cityId){}
  public record ArmSkillRequest(String skillId){}
  public record EquipRequest(Long itemId){}
  public record UnequipRequest(String slot){}

  /** All of the player's heroes (locked ones included, with unlocked=false). */
  @GetMapping("/heroes")
  public List<Map<String,Object>> heroes(){
    return heroes.list(me()).stream().map(heroes::dto).toList();
  }

  @GetMapping("/heroes/{heroId}")
  public Map<String,Object> hero(@PathVariable Long heroId){
    return heroes.dto(heroes.requireOwned(me(), heroId));
  }

  @PostMapping("/heroes/{heroId}/attributes")
  public Map<String,Object> attributes(@PathVariable Long heroId, @RequestBody AttributesRequest r){
    return heroes.dto(heroes.addAttributes(me(), heroId, r.leadership(), r.cunning(), r.valor()));
  }

  @PostMapping("/heroes/{heroId}/station")
  public Map<String,Object> station(@PathVariable Long heroId, @RequestBody StationRequest r){
    return heroes.dto(heroes.station(me(), heroId, r.cityId()));
  }

  @PostMapping("/heroes/{heroId}/arm-skill")
  public Map<String,Object> armSkill(@PathVariable Long heroId, @RequestBody ArmSkillRequest r){
    return heroes.dto(heroes.armSkill(me(), heroId, r.skillId()));
  }

  @PostMapping("/heroes/{heroId}/equip")
  public Map<String,Object> equip(@PathVariable Long heroId, @RequestBody EquipRequest r){
    return heroes.dto(heroes.equipItem(me(), heroId, r.itemId()));
  }

  @PostMapping("/heroes/{heroId}/unequip")
  public Map<String,Object> unequip(@PathVariable Long heroId, @RequestBody UnequipRequest r){
    return heroes.dto(heroes.unequipSlot(me(), heroId, r.slot()));
  }

  /** Player-wide relic inventory (shared across both heroes). */
  @GetMapping("/inventory")
  public List<Map<String,Object>> inventory(){
    var inv = heroes.inventory(me());
    heroes.markInventorySeen(me());
    return inv;
  }
}
