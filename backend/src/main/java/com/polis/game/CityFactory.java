package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Creates fully-provisioned player cities (capital or colony). */
@Service
public class CityFactory {
  private final CityRepo cities;
  private final BuildingRepo buildings;

  public CityFactory(CityRepo cities, BuildingRepo buildings){ this.cities=cities; this.buildings=buildings; }

  private static final Map<BuildingType,Integer> STARTER = Map.of(
      BuildingType.SENATE,1, BuildingType.TIMBER,1, BuildingType.QUARRY,1,
      BuildingType.MINE,1, BuildingType.FARM,2, BuildingType.WAREHOUSE,1);

  @Transactional
  public City createPlayerCity(Long worldId, Long playerId, Long islandId, int slot, String name, boolean capital){
    return createPlayerCity(worldId, playerId, islandId, slot, name, capital, Race.HUMANS);
  }

  @Transactional
  public City createPlayerCity(Long worldId, Long playerId, Long islandId, int slot, String name,
                               boolean capital, Race race){
    City c = new City();
    c.setWorldId(worldId); c.setPlayerId(playerId); c.setIslandId(islandId); c.setSlot(slot);
    c.setName(name); c.setCapital(capital); c.setRace(race);
    c.setWood(500); c.setStone(500); c.setSilver(250);
    c = cities.save(c);
    for (BuildingType t : BuildingType.values())
      buildings.save(new CityBuilding(c.getId(), t, STARTER.getOrDefault(t, 0)));
    c.setPoints(GameRules.cityPoints(java.util.Arrays.stream(BuildingType.values())
        .collect(java.util.stream.Collectors.toMap(t->t, t->STARTER.getOrDefault(t,0)))));
    return cities.save(c);
  }
}
