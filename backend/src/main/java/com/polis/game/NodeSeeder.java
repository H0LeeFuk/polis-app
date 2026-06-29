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
 * Seeds dedicated resource islands (~1 per 4–5 player islands) across the disc. Resource strength
 * scales with the tier the island falls in: rich, high-level nodes guarded by tough bosses cluster
 * in the dangerous core (tier 1), while the outer rim (tier 3, the spawn zone) holds only weak nodes.
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

      List<Island> playerIslands = islands.findByWorldId(wid).stream().filter(i -> i.getTier() > 0).toList();
      if (playerIslands.isEmpty()) continue;
      Random rnd = new Random(wid * 1000 + 7);

      int cx = GameRules.WORLD_CENTER_X, cy = GameRules.WORLD_CENTER_Y;

      // collect existing island positions so nothing overlaps
      List<int[]> taken = new ArrayList<>();
      for (Island i : islands.findByWorldId(wid)) taken.add(new int[]{ i.getPx(), i.getPy() });

      int resourceIslandCount = Math.max(3, playerIslands.size() / 4);
      // Split the islands across the three tier bands as evenly as possible, then place each tier's
      // share at EVENLY-SPACED angles around its ring (not random) so no player island ends up with a
      // cluster of resource isles next door while another has none — every angular sector gets one.
      int[] perTier = new int[4];
      for (int k = 0; k < resourceIslandCount; k++) perTier[1 + (k % 3)]++;

      int typeCursor = 0, k = -1;
      for (int tier = 1; tier <= 3; tier++){
        int countT = perTier[tier];
        for (int j = 0; j < countT; j++){
        k++;
        Race race = RACES[k % RACES.length];   // race-themed island
        // even angle for this tier's share, offset so resource isles sit between the player islands
        double baseAngle = (2 * Math.PI * j / countT) + (tier * 0.5) + (Math.PI / countT);
        int[] pos = ringSpot(cx, cy, GameRules.TIER_INNER[tier], GameRules.TIER_OUTER[tier], baseAngle, taken, rnd);
        taken.add(pos);
        Island ri = new Island();
        ri.setWorldId(wid); ri.setName(ISLE_NAME.get(race) + " " + (k + 1));
        ri.setPx(pos[0]); ri.setPy(pos[1]);
        ri.setOceanX(42); ri.setOceanY(42); ri.setSeed(rnd.nextInt(1_000_000));
        ri.setResource(true); ri.setTier(0);
        ri = islands.save(ri);

        // node level by tier: core 4–5, mid 2–4, rim 1–2
        int lo = switch (tier){ case 1 -> 4; case 2 -> 2; default -> 1; };
        int hi = switch (tier){ case 1 -> 5; case 2 -> 4; default -> 2; };

        for (int n = 0; n < 2; n++){
          ResourceNode node = new ResourceNode();
          node.setWorldId(wid); node.setIslandId(ri.getId());
          node.setX((int)(40 + rnd.nextInt(40))); node.setY((int)(50 + rnd.nextInt(50)));
          node.setNodeType(TYPES[typeCursor++ % TYPES.length]);
          node.setLevel(lo + rnd.nextInt(hi - lo + 1));
          node.setStatus(NodeStatus.UNCLAIMED);
          nodes.save(node);
        }

        // one guardian boss; stronger toward the core
        if (bosses.findByIslandId(ri.getId()).isEmpty()){
          int level = (switch (tier){ case 1 -> 5; case 2 -> 3; default -> 2; }) + rnd.nextInt(2);
          IslandBoss boss = new IslandBoss();
          boss.setWorldId(wid); boss.setIslandId(ri.getId());
          boss.setRace(race); boss.setName(bossService.bossName(race)); boss.setLevel(level);
          boss.setDefenderTroops(IslandBossService.defendersFor(level));
          bosses.save(boss);
        }
        }
      }
    }
  }

  private static final int MIN_GAP = 190;
  /** A spot in the [rIn,rOut] band near {@code baseAngle}, nudged (small angular jitter) until it
   *  clears other islands — keeps each resource island in its assigned, evenly-spaced sector. */
  private int[] ringSpot(int cx, int cy, double rIn, double rOut, double baseAngle, List<int[]> taken, Random rnd){
    for (int a = 0; a < 200; a++){
      double ang = baseAngle + (rnd.nextDouble() - 0.5) * 0.4;   // stay within this sector
      double r = rIn + rnd.nextDouble() * (rOut - rIn);
      int px = (int)Math.round(cx + Math.cos(ang) * r);
      int py = (int)Math.round(cy + Math.sin(ang) * r);
      if (farEnough(px, py, taken)) return new int[]{px, py};
    }
    double ang = baseAngle, r = (rIn + rOut) / 2;
    return new int[]{ (int)Math.round(cx + Math.cos(ang)*r), (int)Math.round(cy + Math.sin(ang)*r) };
  }
  private boolean farEnough(int px, int py, List<int[]> taken){
    for (int[] t : taken) if (Math.hypot(px - t[0], py - t[1]) < MIN_GAP) return false;
    return true;
  }
}
