package com.polis.game;

import com.polis.domain.BuildingType;
import com.polis.domain.City;
import com.polis.domain.CityBuilding;
import com.polis.repo.BuildingRepo;
import com.polis.repo.CityRepo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * One-shot migration helper: ensures every city has a {@link CityBuilding} row for EVERY
 * {@link BuildingType}. Cities created before a new building type was added (e.g. WATCHTOWER) were
 * missing its row, so it never appeared on the city map / could not be built. Adds any missing row
 * at level 0. Idempotent — cities that already have all rows are untouched.
 */
@Component
@Order(25)
public class CityBuildingBackfillRunner implements ApplicationRunner {
  private final CityRepo cities;
  private final BuildingRepo buildings;

  public CityBuildingBackfillRunner(CityRepo cities, BuildingRepo buildings){
    this.cities = cities; this.buildings = buildings;
  }

  @Override @Transactional
  public void run(ApplicationArguments args){
    for (City c : cities.findAll()){
      Set<BuildingType> have = EnumSet.noneOf(BuildingType.class);
      for (CityBuilding b : buildings.findByCityId(c.getId())) have.add(b.getType());
      for (BuildingType t : BuildingType.values())
        if (!have.contains(t)) buildings.save(new CityBuilding(c.getId(), t, 0));
    }
  }
}
