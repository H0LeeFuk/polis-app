package com.polis.game;

import com.polis.domain.City;
import com.polis.domain.CityGroup;
import com.polis.domain.CityGroupMembership;
import com.polis.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * City Groups service tests (ownership/permission + many-to-many semantics). Repos are mocked —
 * this verifies the service's validation, ownership checks, and idempotent membership handling.
 */
class CityGroupServiceTest {
  private static final long ME = 1L, OTHER = 2L, GID = 10L;

  private CityGroupRepo groups;
  private CityGroupMembershipRepo memberships;
  private CityRepo cities;
  private CityGroupService svc;

  private static City city(long id){ City c = new City(); c.setId(id); return c; }
  private CityGroup group(long owner){ CityGroup g = new CityGroup(); g.setId(GID); g.setOwnerPlayerId(owner); g.setName("G"); g.setIcon("🏰"); return g; }

  @BeforeEach void setUp(){
    groups = mock(CityGroupRepo.class);
    memberships = mock(CityGroupMembershipRepo.class);
    cities = mock(CityRepo.class);
    svc = new CityGroupService(groups, memberships, cities, mock(IslandRepo.class), mock(MovementRepo.class), mock(JobRepo.class));
    lenient().when(cities.findByPlayerId(ME)).thenReturn(List.of(city(101), city(102)));
  }

  @Test void create_rejects_blank_name(){
    assertThatThrownBy(() -> svc.create(ME, "  ", "🏰")).isInstanceOf(IllegalStateException.class);
    verify(groups, never()).save(any());
  }

  @Test void create_assigns_next_sort_order_and_saves(){
    when(groups.findByOwnerPlayerIdOrderBySortOrderAscIdAsc(ME)).thenReturn(List.of());
    when(groups.save(any())).thenAnswer(inv -> { CityGroup g = inv.getArgument(0); g.setId(7L); return g; });
    var out = svc.create(ME, "Frontier", "⚔");
    assertThat(out).containsEntry("id", 7L).containsEntry("name", "Frontier").containsEntry("sortOrder", 0);
  }

  @Test void addCities_adds_only_my_cities_and_skips_existing_membership(){
    when(groups.findById(GID)).thenReturn(Optional.of(group(ME)));
    when(memberships.existsByCityGroupIdAndCityId(GID, 101L)).thenReturn(true);   // already a member
    when(memberships.existsByCityGroupIdAndCityId(GID, 102L)).thenReturn(false);
    svc.addCities(ME, GID, List.of(101L, 102L, 999L));   // 999 is not the player's city
    // only 102 is saved: 101 already a member, 999 is foreign
    verify(memberships, times(1)).save(any(CityGroupMembership.class));
  }

  @Test void addCities_on_someone_elses_group_is_rejected(){
    when(groups.findById(GID)).thenReturn(Optional.of(group(OTHER)));
    assertThatThrownBy(() -> svc.addCities(ME, GID, List.of(101L))).isInstanceOf(IllegalStateException.class);
    verify(memberships, never()).save(any());
  }

  @Test void removeCities_only_touches_my_cities(){
    when(groups.findById(GID)).thenReturn(Optional.of(group(ME)));
    svc.removeCities(ME, GID, List.of(101L, 999L));
    verify(memberships).deleteByCityGroupIdAndCityIdIn(eq(GID), argThat(c -> c.contains(101L) && !c.contains(999L)));
  }

  @Test void delete_removes_memberships_then_the_group(){
    when(groups.findById(GID)).thenReturn(Optional.of(group(ME)));
    svc.delete(ME, GID);
    verify(memberships).deleteByCityGroupId(GID);
    verify(groups).deleteById(GID);
  }

  @Test void edit_rejects_overlong_name(){
    when(groups.findById(GID)).thenReturn(Optional.of(group(ME)));
    assertThatThrownBy(() -> svc.edit(ME, GID, "x".repeat(41), null, null)).isInstanceOf(IllegalStateException.class);
    verify(groups, never()).save(any());
  }

  @Test void unknown_group_throws(){
    when(groups.findById(anyLong())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> svc.delete(ME, 555L)).isInstanceOf(IllegalArgumentException.class);
  }
}
