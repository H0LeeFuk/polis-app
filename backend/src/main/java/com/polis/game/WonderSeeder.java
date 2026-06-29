package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the three Wonders of the Aegean — one per Wonder Island at the world's Heart — on first
 * boot. They are created DORMANT (visible but inert) and activate when the world enters its endgame.
 * Idempotent: a world that already has Wonders is skipped.
 */
@Component
@Order(30)   // after WorldSeeder (10) and NodeSeeder (20)
public class WonderSeeder implements ApplicationRunner {
  private final WorldRepo worlds; private final IslandRepo islands; private final WonderRepo wonders;

  public WonderSeeder(WorldRepo worlds, IslandRepo islands, WonderRepo wonders){
    this.worlds = worlds; this.islands = islands; this.wonders = wonders;
  }

  private record Spec(WonderKind kind, String name, String island, int dx, int dy){}
  // offsets from the world centre (the Heart) — a tight triangle within HEART_RADIUS
  private static final Spec[] SPECS = {
    new Spec(WonderKind.LIGHTHOUSE, "The Pharos Eternal",  "Heart of Aegea",     0, -110),
    new Spec(WonderKind.COLOSSUS,   "Colossus of Helios",  "Sunspire Atoll",   -95,   60),
    new Spec(WonderKind.SANCTUM,    "Sanctum of the Tides","Maelstrom Crown",   95,   60),
  };

  @Override @Transactional
  public void run(ApplicationArguments args){
    for (World w : worlds.findAll()){
      Long wid = w.getId();
      if (!wonders.findByWorldId(wid).isEmpty()) continue;   // already seeded
      for (Spec sp : SPECS){
        Island ri = new Island();
        ri.setWorldId(wid); ri.setName(sp.island());
        ri.setPx(GameRules.WORLD_CENTER_X + sp.dx()); ri.setPy(GameRules.WORLD_CENTER_Y + sp.dy());
        ri.setOceanX(42); ri.setOceanY(42); ri.setSeed(0); ri.setResource(false); ri.setTier(0);
        ri = islands.save(ri);

        Wonder wo = new Wonder();
        wo.setWorldId(wid); wo.setIslandId(ri.getId());
        wo.setName(sp.name()); wo.setWonderKind(sp.kind());
        wo.setX(50); wo.setY(50); wo.setLevel(0); wo.setStatus(WonderStatus.DORMANT);
        wonders.save(wo);
      }
    }
  }
}
