package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Attaches resource nodes + a guardian boss to every GROUP-CENTRE resource island (one per group).
 * Resource strength scales inward by tier: rich, high-level nodes guarded by tough bosses sit in the
 * dangerous core (tier 3), while the outer rim (tier 1, the spawn zone) holds only weak nodes.
 * Idempotent per world.
 */
@Component
@Order(20)   // after WorldSeeder
public class NodeSeeder implements ApplicationRunner {
  private final WorldRepo worlds; private final IslandRepo islands; private final ResourceNodeRepo nodes;
  private final IslandBossRepo bosses; private final IslandBossService bossService;

  public NodeSeeder(WorldRepo worlds, IslandRepo islands, ResourceNodeRepo nodes,
                    IslandBossRepo bosses, IslandBossService bossService){
    this.worlds = worlds; this.islands = islands; this.nodes = nodes;
    this.bosses = bosses; this.bossService = bossService;
  }

  private static final NodeType[] TYPES = NodeType.values();
  private static final Race[] RACES = Race.values();
  private static final Map<Race,String> ISLE_NAME = Map.of(
      Race.HUMANS, "Vale Sanctuary", Race.GIANTS, "Stonehold Isle",
      Race.FAIRIES, "Glimmerwood", Race.NEWTS, "Tidewatch Reef");

  @Override @Transactional
  public void run(ApplicationArguments args){
    for (World w : worlds.findAll()){
      Long wid = w.getId();
      if (!nodes.findByWorldId(wid).isEmpty()) continue;   // already seeded for this world

      // every group's centre yellow island (1 resource island per group)
      List<Island> resourceIslands = islands.findByWorldId(wid).stream()
          .filter(i -> i.isResource() && i.getClusterId() > 0 && i.getTier() > 0).toList();
      if (resourceIslands.isEmpty()) continue;
      Random rnd = new Random(wid * 1000 + 7);

      int typeCursor = 0, k = -1;
      for (Island ri : resourceIslands){
        k++;
        int tier = ri.getTier();                 // 1 outer/weak .. 3 core/strong
        Race race = RACES[k % RACES.length];      // race-themed guardian
        if (ri.getName() == null || ri.getName().isBlank()) ri.setName(ISLE_NAME.get(race) + " " + (k + 1));

        // node level by tier: core (T3) 4–5, mid (T2) 2–4, rim (T1) 1–2
        int lo = switch (tier){ case 3 -> 4; case 2 -> 2; default -> 1; };
        int hi = switch (tier){ case 3 -> 5; case 2 -> 4; default -> 2; };

        for (int n = 0; n < 2; n++){
          ResourceNode node = new ResourceNode();
          node.setWorldId(wid); node.setIslandId(ri.getId());
          node.setX((int)(40 + rnd.nextInt(40))); node.setY((int)(50 + rnd.nextInt(50)));
          node.setNodeType(TYPES[typeCursor++ % TYPES.length]);
          node.setLevel(lo + rnd.nextInt(hi - lo + 1));
          node.setStatus(NodeStatus.UNCLAIMED);
          nodes.save(node);
        }

        // one guardian boss; stronger toward the core (T3) — Colossus-style shared HP pool, per-player rewards
        if (bosses.findByIslandId(ri.getId()).isEmpty()){
          int level = (switch (tier){ case 3 -> 5; case 2 -> 3; default -> 2; }) + rnd.nextInt(2);
          IslandBoss boss = new IslandBoss();
          boss.setWorldId(wid); boss.setIslandId(ri.getId());
          boss.setRace(race); boss.setName(bossService.bossName(race)); boss.setLevel(level);
          boss.setTier(tier);
          boss.setDefenderTroops(IslandBossService.defendersFor(level));
          bossService.initSpawn(boss);   // HP from level + rolled elemental profile
          bosses.save(boss);
        }
      }
    }
  }
}
