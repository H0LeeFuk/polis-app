package com.polis.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="cities")
@Getter @Setter @NoArgsConstructor
public class City {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="world_id", nullable=false) private Long worldId;
  @Column(name="player_id") private Long playerId;          // null => barbarian
  @Column(name="island_id", nullable=false) private Long islandId;
  private int slot;
  private String name;
  @Column(name="is_capital") private boolean capital = false;
  /** The city's race — chosen permanently when founded. Drives passive {@link Race} bonuses. */
  @Enumerated(EnumType.STRING) private Race race;
  // base resources (every city) + the four special resources (only the race's own is produced;
  // others can arrive via looting/trade). Silver and Favor were removed in the resource rework.
  private double wood, stone, wheat;
  private double coal, crystals, iron, pearls;
  private double power;
  private int points;
  @Column(name="last_tick_at") private Instant lastTickAt = Instant.now();
  private Instant createdAt = Instant.now();
  // NOTE: optimistic @Version removed — the per-read sync() + 5s background tick
  // both write cities, so version checks lost frequent races (500s on build/train).
  // Last-write-wins is acceptable here; the DB 'version' column is left unused.

  /** Read any resource amount by type — keeps trade/loot/production generic over all 7 resources. */
  @Transient public double get(ResourceType rt){
    return switch (rt){
      case WOOD -> wood; case STONE -> stone; case WHEAT -> wheat;
      case COAL -> coal; case CRYSTALS -> crystals; case IRON -> iron; case PEARLS -> pearls;
    };
  }
  @Transient public void set(ResourceType rt, double v){
    switch (rt){
      case WOOD -> wood=v; case STONE -> stone=v; case WHEAT -> wheat=v;
      case COAL -> coal=v; case CRYSTALS -> crystals=v; case IRON -> iron=v; case PEARLS -> pearls=v;
    }
  }
  @Transient public void add(ResourceType rt, double delta){ set(rt, get(rt)+delta); }
}
