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
 * Seeds dedicated resource islands (~1 per 4–5 player islands) hosting 1–3 nodes each.
 * Idempotent per world, so existing worlds gain nodes on next boot. Higher-level nodes are
 * biased toward the central region of the map (more dangerous, away from the edges).
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

      List<Island> playerIslands = islands.findByWorldId(wid).stream().filter(i -> !i.isResource()).toList();
      if (playerIslands.isEmpty()) continue;
      Random rnd = new Random(wid * 1000 + 7);

      // map centre, to bias high-level nodes toward the middle
      double cx = playerIslands.stream().mapToInt(Island::getPx).average().orElse(700);
      double cy = playerIslands.stream().mapToInt(Island::getPy).average().orElse(500);

      // collect all existing island positions so nothing is placed on top of another
      List<int[]> taken = new ArrayList<>();
      for (Island i : islands.findByWorldId(wid)) taken.add(new int[]{ i.getPx(), i.getPy() });

      int resourceIslandCount = Math.max(3, playerIslands.size() / 2);
      int typeCursor = 0;
      for (int k = 0; k < resourceIslandCount; k++){
        Race race = RACES[k % RACES.length];   // race-themed island
        int[] pos = freeSpot(taken, rnd);
        taken.add(pos);
        Island ri = new Island();
        ri.setWorldId(wid); ri.setName(ISLE_NAME.get(race) + " " + (k + 1));
        ri.setPx(pos[0]); ri.setPy(pos[1]);
        ri.setOceanX(42); ri.setOceanY(42); ri.setSeed(rnd.nextInt(1_000_000));
        ri.setResource(true);
        ri = islands.save(ri);

        double distToCentre = Math.hypot(ri.getPx() - cx, ri.getPy() - cy);
        boolean central = distToCentre < 250;

        // exactly 2 resource "buildings" (nodes) per island
        for (int n = 0; n < 2; n++){
          ResourceNode node = new ResourceNode();
          node.setWorldId(wid); node.setIslandId(ri.getId());
          node.setX((int)(40 + rnd.nextInt(40))); node.setY((int)(50 + rnd.nextInt(50)));
          node.setNodeType(TYPES[typeCursor++ % TYPES.length]);
          node.setLevel(central ? 3 + rnd.nextInt(3) : 1 + rnd.nextInt(3));
          node.setStatus(NodeStatus.UNCLAIMED);
          nodes.save(node);
        }

        // 1 guardian boss per resource island
        if (bosses.findByIslandId(ri.getId()).isEmpty()){
          int level = (central ? 4 : 2) + rnd.nextInt(2);
          IslandBoss boss = new IslandBoss();
          boss.setWorldId(wid); boss.setIslandId(ri.getId());
          boss.setRace(race); boss.setName(bossService.bossName(race)); boss.setLevel(level);
          boss.setDefenderTroops(IslandBossService.defendersFor(level));
          bosses.save(boss);
        }
      }
    }
  }

  /** Minimum centre-to-centre spacing so islands never overlap / sit in front of each other. */
  private static final int MIN_GAP = 240;
  private static final int MAP_W = 1500, MAP_H = 1100, MARGIN = 120;

  private int[] freeSpot(List<int[]> taken, Random rnd){
    // try random spots first
    for (int a = 0; a < 200; a++){
      int px = MARGIN + rnd.nextInt(MAP_W - 2*MARGIN);
      int py = MARGIN + rnd.nextInt(MAP_H - 2*MARGIN);
      if (farEnough(px, py, taken)) return new int[]{px, py};
    }
    // fallback: scan a grid for the first free cell
    for (int py = MARGIN; py < MAP_H - MARGIN; py += MIN_GAP)
      for (int px = MARGIN; px < MAP_W - MARGIN; px += MIN_GAP)
        if (farEnough(px, py, taken)) return new int[]{px, py};
    return new int[]{ MARGIN + rnd.nextInt(MAP_W - 2*MARGIN), MARGIN + rnd.nextInt(MAP_H - 2*MARGIN) };
  }
  private boolean farEnough(int px, int py, List<int[]> taken){
    for (int[] t : taken) if (Math.hypot(px - t[0], py - t[1]) < MIN_GAP) return false;
    return true;
  }
}
