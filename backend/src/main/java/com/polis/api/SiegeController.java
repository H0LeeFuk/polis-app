package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.MovementService;
import com.polis.game.SiegeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Siege lifecycle endpoints. Starting a siege goes through the normal attack flow with
 * {@code intent:"SIEGE"} (see {@link CityController#attack}); these cover everything once a siege
 * exists: read views, attacker/ally reinforcement, defender/ally break attempts, and withdrawal.
 */
@RestController
@RequestMapping("/api")
public class SiegeController {
  private final SiegeService sieges;
  private final MovementService movements;
  public SiegeController(SiegeService sieges, MovementService movements){
    this.sieges = sieges; this.movements = movements;
  }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record ReinforceRequest(Long fromCityId, Map<String,Integer> troops){}
  public record SiegeAttackRequest(Long fromCityId, Map<String,Integer> troops, Long includeHeroId){}

  @GetMapping("/sieges/{siegeId}")
  public Map<String,Object> get(@PathVariable Long siegeId){ return sieges.siegeView(me(), siegeId); }

  @GetMapping("/cities/{cityId}/siege")
  public Map<String,Object> citySiege(@PathVariable Long cityId){
    Map<String,Object> v = sieges.citySiege(me(), cityId);
    return v == null ? java.util.Collections.singletonMap("siege", null) : v;
  }

  @GetMapping("/players/me/sieges")
  public List<Map<String,Object>> mine(){ return sieges.mySieges(me()); }

  /** Every unresolved movement heading to the besieged city — attacks, supports, siege reinforcements. */
  @GetMapping("/sieges/{siegeId}/movements")
  public List<MovementDTO> siegeMovements(@PathVariable Long siegeId){
    Long me = me();
    return movements.movementsToCity(me, sieges.participantCityId(me, siegeId));
  }

  @PostMapping("/sieges/{siegeId}/reinforce")
  public MovementDTO reinforce(@PathVariable Long siegeId, @RequestBody ReinforceRequest r){
    Long me = me();
    return movements.dto(sieges.reinforce(me, siegeId, r.fromCityId(), r.troops()), me);
  }

  @PostMapping("/sieges/{siegeId}/attack")
  public MovementDTO attack(@PathVariable Long siegeId, @RequestBody SiegeAttackRequest r){
    Long me = me();
    return movements.dto(sieges.attackSiege(me, siegeId, r.fromCityId(), r.troops(), r.includeHeroId()), me);
  }

  @PostMapping("/sieges/{siegeId}/withdraw")
  public Map<String,Object> withdraw(@PathVariable Long siegeId){
    sieges.withdraw(me(), siegeId); return Map.of("ok", true);
  }
}
