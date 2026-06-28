package com.polis.repo;

import com.polis.domain.Hero;
import com.polis.domain.HeroKey;
import com.polis.domain.HeroState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HeroRepo extends JpaRepository<Hero, Long> {
  List<Hero> findByOwnerPlayerId(Long ownerPlayerId);
  Optional<Hero> findByOwnerPlayerIdAndHeroKey(Long ownerPlayerId, HeroKey heroKey);
  Optional<Hero> findByActiveMovementId(Long activeMovementId);
  List<Hero> findByStationedCityIdAndStateAndUnlockedTrue(Long stationedCityId, HeroState state);
  List<Hero> findByStateAndWoundedUntilLessThanEqual(HeroState state, Instant when);
}
