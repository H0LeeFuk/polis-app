package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * City Groups — per-player, purely-organizational grouping of a player's own cities (UI/navigation
 * only, no gameplay effect). Many-to-many: a city may sit in several groups. All mutations are
 * ownership-checked (group.ownerPlayerId and city.playerId must match the caller).
 */
@Service
public class CityGroupService {
  /** Preset group icons the UI offers; server validates leniently (see {@link #validIcon}). */
  public static final List<String> ICON_CHOICES = List.of(
      "🏰","⚔","🛡","🏛","⚓","🔥","🌊","🗿","🧚","🐉","👑","⭐","🏹","💰","🌲","⛏");

  private final CityGroupRepo groups;
  private final CityGroupMembershipRepo memberships;
  private final CityRepo cities;
  private final IslandRepo islands;
  private final MovementRepo movements;
  private final JobRepo jobs;

  public CityGroupService(CityGroupRepo groups, CityGroupMembershipRepo memberships, CityRepo cities,
                          IslandRepo islands, MovementRepo movements, JobRepo jobs){
    this.groups = groups; this.memberships = memberships; this.cities = cities;
    this.islands = islands; this.movements = movements; this.jobs = jobs;
  }

  private static boolean validIcon(String icon){ return icon != null && !icon.isBlank() && icon.length() <= 8; }

  private CityGroup ownedGroup(Long playerId, Long groupId){
    CityGroup g = groups.findById(groupId).orElseThrow(() -> new IllegalArgumentException("Group not found"));
    if (!Objects.equals(g.getOwnerPlayerId(), playerId)) throw new IllegalStateException("Not your group");
    return g;
  }

  /** Only the caller's own city ids, from the requested set (silently drops any that aren't theirs). */
  private List<Long> myCityIds(Long playerId, Collection<Long> requested){
    Set<Long> owned = cities.findByPlayerId(playerId).stream().map(City::getId).collect(Collectors.toSet());
    if (requested == null) return List.of();
    return requested.stream().filter(owned::contains).distinct().toList();
  }

  // ---- reads --------------------------------------------------------------

  /** All the player's groups with their member city ids, plus the icon preset. */
  @Transactional(readOnly = true)
  public Map<String,Object> list(Long playerId){
    List<CityGroup> gs = groups.findByOwnerPlayerIdOrderBySortOrderAscIdAsc(playerId);
    Map<Long,List<Long>> byGroup = new HashMap<>();
    if (!gs.isEmpty())
      for (CityGroupMembership m : memberships.findByCityGroupIdIn(gs.stream().map(CityGroup::getId).toList()))
        byGroup.computeIfAbsent(m.getCityGroupId(), k -> new ArrayList<>()).add(m.getCityId());

    List<Map<String,Object>> out = new ArrayList<>();
    for (CityGroup g : gs){
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("id", g.getId()); m.put("name", g.getName()); m.put("icon", g.getIcon());
      m.put("sortOrder", g.getSortOrder());
      m.put("cityIds", byGroup.getOrDefault(g.getId(), List.of()));
      out.add(m);
    }
    Map<String,Object> res = new LinkedHashMap<>();
    res.put("groups", out);
    res.put("iconChoices", ICON_CHOICES);
    return res;
  }

  /**
   * Every one of the player's cities with light summary info for the grouped switcher:
   * name, island, capital flag, the group ids it belongs to, an "under attack" flag, and what
   * (if anything) it is currently building.
   */
  @Transactional(readOnly = true)
  public List<Map<String,Object>> citiesOverview(Long playerId){
    List<City> owned = cities.findByPlayerId(playerId);
    owned.sort(Comparator.comparing(City::isCapital).reversed()
        .thenComparing(City::getName, Comparator.nullsLast(String::compareTo)));
    Set<Long> ids = owned.stream().map(City::getId).collect(Collectors.toSet());

    // membership: cityId -> [groupId]
    Map<Long,List<Long>> cityGroups = new HashMap<>();
    if (!ids.isEmpty())
      for (CityGroupMembership m : memberships.findByCityIdIn(ids))
        cityGroups.computeIfAbsent(m.getCityId(), k -> new ArrayList<>()).add(m.getCityGroupId());

    // incoming hostile armies → under-attack set
    Set<Long> underAttack = new HashSet<>();
    if (!ids.isEmpty())
      for (Movement mv : movements.findByTargetCityIdInAndResolvedFalse(ids))
        if (mv.getPhase() == MovementPhase.OUT && !Objects.equals(mv.getPlayerId(), playerId) && mv.getTargetCityId() != null)
          underAttack.add(mv.getTargetCityId());

    Map<Long,String> islandNames = new HashMap<>();
    List<Map<String,Object>> out = new ArrayList<>();
    for (City c : owned){
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("id", c.getId());
      m.put("name", c.getName());
      m.put("capital", c.isCapital());
      m.put("island", islandNames.computeIfAbsent(c.getIslandId(),
          k -> islands.findById(k).map(Island::getName).orElse("?")));
      m.put("raceName", c.getRace() == null ? null : c.getRace().displayName);
      m.put("groupIds", cityGroups.getOrDefault(c.getId(), List.of()));
      m.put("underAttack", underAttack.contains(c.getId()));
      m.put("building", currentBuild(c.getId()));
      out.add(m);
    }
    return out;
  }

  /** The city's current construction (queue head), or null if idle. */
  private Map<String,Object> currentBuild(Long cityId){
    List<BuildJob> q = jobs.findByCityIdAndQueueTypeOrderByPositionAsc(cityId, QueueType.BUILDING);
    if (q.isEmpty()) return null;
    BuildJob head = q.get(0);
    if (head.getBuildingType() == null) return null;
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("type", head.getBuildingType().name());
    m.put("toLevel", head.getToLevel());
    return m;
  }

  // ---- mutations ----------------------------------------------------------

  @Transactional
  public Map<String,Object> create(Long playerId, String name, String icon){
    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > 40) throw new IllegalStateException("Name must be 1–40 characters");
    if (!validIcon(icon)) throw new IllegalStateException("Pick a valid icon");
    int nextOrder = groups.findByOwnerPlayerIdOrderBySortOrderAscIdAsc(playerId).stream()
        .mapToInt(CityGroup::getSortOrder).max().orElse(-1) + 1;
    CityGroup g = new CityGroup();
    g.setOwnerPlayerId(playerId); g.setName(n); g.setIcon(icon.trim()); g.setSortOrder(nextOrder);
    g = groups.save(g);
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("id", g.getId()); out.put("name", g.getName()); out.put("icon", g.getIcon()); out.put("sortOrder", g.getSortOrder());
    return out;
  }

  @Transactional
  public void edit(Long playerId, Long groupId, String name, String icon, Integer sortOrder){
    CityGroup g = ownedGroup(playerId, groupId);
    if (name != null){
      String n = name.trim();
      if (n.isEmpty() || n.length() > 40) throw new IllegalStateException("Name must be 1–40 characters");
      g.setName(n);
    }
    if (icon != null){
      if (!validIcon(icon)) throw new IllegalStateException("Pick a valid icon");
      g.setIcon(icon.trim());
    }
    if (sortOrder != null) g.setSortOrder(sortOrder);
    groups.save(g);
  }

  @Transactional
  public void delete(Long playerId, Long groupId){
    ownedGroup(playerId, groupId);
    memberships.deleteByCityGroupId(groupId);   // cities themselves are untouched
    groups.deleteById(groupId);
  }

  @Transactional
  public void addCities(Long playerId, Long groupId, Collection<Long> cityIds){
    ownedGroup(playerId, groupId);
    for (Long cid : myCityIds(playerId, cityIds))
      if (!memberships.existsByCityGroupIdAndCityId(groupId, cid))
        memberships.save(new CityGroupMembership(groupId, cid));
  }

  @Transactional
  public void removeCities(Long playerId, Long groupId, Collection<Long> cityIds){
    ownedGroup(playerId, groupId);
    List<Long> mine = myCityIds(playerId, cityIds);
    if (!mine.isEmpty()) memberships.deleteByCityGroupIdAndCityIdIn(groupId, mine);
  }
}
