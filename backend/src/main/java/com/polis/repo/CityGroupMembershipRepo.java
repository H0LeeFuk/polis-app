package com.polis.repo;

import com.polis.domain.CityGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface CityGroupMembershipRepo extends JpaRepository<CityGroupMembership, Long> {
  List<CityGroupMembership> findByCityGroupId(Long cityGroupId);
  List<CityGroupMembership> findByCityGroupIdIn(Collection<Long> cityGroupIds);
  List<CityGroupMembership> findByCityIdIn(Collection<Long> cityIds);
  boolean existsByCityGroupIdAndCityId(Long cityGroupId, Long cityId);

  @Transactional void deleteByCityGroupId(Long cityGroupId);
  @Transactional void deleteByCityGroupIdAndCityIdIn(Long cityGroupId, Collection<Long> cityIds);
  @Transactional void deleteByCityId(Long cityId);
}
