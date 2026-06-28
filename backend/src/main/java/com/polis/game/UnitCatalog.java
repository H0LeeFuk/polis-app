package com.polis.game;

import com.polis.domain.QueueType;
import com.polis.domain.UnitType;
import com.polis.repo.UnitTypeRepo;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * In-memory view of the seeded {@code unit_types} table. The catalog is immutable at
 * runtime, so it is loaded once and cached; everything else looks up unit stats by name
 * through here instead of the old {@code UnitType} enum.
 */
@Service
public class UnitCatalog {
  private final UnitTypeRepo repo;
  private volatile Map<String,UnitType> byName = Map.of();
  private volatile List<UnitType> all = List.of();

  public UnitCatalog(UnitTypeRepo repo){ this.repo = repo; }

  @PostConstruct
  public synchronized void reload(){
    List<UnitType> rows = repo.findAll();
    Map<String,UnitType> m = new LinkedHashMap<>();
    for (UnitType u : rows) m.put(u.getName().toUpperCase(), u);
    this.byName = m;
    this.all = List.copyOf(rows);
  }

  /** Lazily load if Flyway seeded after construction (defensive — normally @PostConstruct wins). */
  private Map<String,UnitType> map(){
    if (byName.isEmpty()){ reload(); }
    return byName;
  }

  public UnitType get(String name){
    if (name == null) throw new IllegalArgumentException("No unit type given");
    UnitType u = map().get(name.toUpperCase());
    if (u == null) throw new IllegalArgumentException("Unknown unit type: " + name);
    return u;
  }

  public boolean exists(String name){ return name != null && map().containsKey(name.toUpperCase()); }

  public List<UnitType> all(){ if (all.isEmpty()) reload(); return all; }

  public List<UnitType> byQueue(QueueType qt){
    return all().stream().filter(u -> u.getFromQueue()==qt).toList();
  }
}
