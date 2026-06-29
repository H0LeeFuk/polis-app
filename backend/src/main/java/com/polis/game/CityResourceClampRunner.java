package com.polis.game;

import com.polis.domain.BuildingType;
import com.polis.domain.City;
import com.polis.domain.CityBuilding;
import com.polis.domain.ResourceType;
import com.polis.repo.BuildingRepo;
import com.polis.repo.CityRepo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot cleanup: the old TEST MODE pinned every player city's resources at 1e9, far above
 * any warehouse cap. This clamps every city's stored resources down to its warehouse cap so
 * no account keeps "infinite" resources. Ongoing capping is handled in {@link CityService#sync}.
 */
@Component
@Order(40)
public class CityResourceClampRunner implements ApplicationRunner {
  private final CityRepo cities;
  private final BuildingRepo buildings;

  public CityResourceClampRunner(CityRepo cities, BuildingRepo buildings){
    this.cities = cities; this.buildings = buildings;
  }

  @Override @Transactional
  public void run(ApplicationArguments args){
    for (City c : cities.findAll()){
      int warehouse = 0;
      for (CityBuilding b : buildings.findByCityId(c.getId()))
        if (b.getType()==BuildingType.WAREHOUSE) warehouse = b.getLevel();
      long cap = GameRules.storeCap(warehouse);
      boolean changed = false;
      for (ResourceType rt : ResourceType.values())
        if (c.get(rt) > cap){ c.set(rt, cap); changed = true; }
      if (changed) cities.save(c);
    }
  }
}
