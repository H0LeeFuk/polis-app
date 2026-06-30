package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Seeds a single disc-shaped world made of GROUPS of 6 islands plus scattered loners.
 *
 * <p><b>Group</b> = 1 YELLOW resource island at the centre + 5 player islands evenly ringed around it
 * at the SAME distance (a regular pentagon). The 5 surrounding islands are GREEN — the only places a
 * player may spawn. Resource-island yield scales inward by tier (Tier 1 outer/weak → Tier 3 core/strong).
 *
 * <p><b>Scattered islands</b> = standalone RED player islands placed in the open water OUTSIDE every
 * group's circle (never inside a group's radius). Players cannot spawn on these — they are reached
 * only by founding on a free slot (or conquering).
 */
@Component
@Order(10)
public class WorldSeeder implements ApplicationRunner {
  private final WorldRepo worlds; private final IslandRepo islands; private final CityRepo cities;
  private final PlayerRepo players; private final AllianceRepo alliances;

  public WorldSeeder(WorldRepo worlds, IslandRepo islands, CityRepo cities, PlayerRepo players, AllianceRepo alliances){
    this.worlds=worlds; this.islands=islands; this.cities=cities; this.players=players; this.alliances=alliances;
  }

  private static final String[] NAMES = {
      "Thalassia","Keros","Andros","Mytilos","Skarpa","Helike","Pyrgos","Lemnos","Aigai","Doria",
      "Naxara","Kymos","Elaia","Serifos","Halki","Ortygia","Patmos","Delos","Ikaros","Syros",
      "Kasos","Melos","Tenara","Zephyra","Korais","Aulis","Phaedra","Olynth"};
  private static final String[] ROMAN = { "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X", " XI", " XII" };

  // groups per ring (index = tier: 1 outer .. 3 core) and the ring radius each sits on
  private static final int[]    GROUPS_PER_TIER = { 0, 12, 8, 3 };
  private static final double[] RING_RADIUS     = { 0, 2150, 1400, 700 };
  private static final double GROUP_RADIUS  = 290;    // distance from the resource centre to each of the 5 surrounding islands
  private static final double GROUP_CIRCLE  = GROUP_RADIUS + 230;   // keep scattered loners this far from any group centre
  private static final int    SCATTERED     = 55;     // standalone found-only islands in open water
  private static final int    MIN_GAP       = 230;    // min centre-to-centre island spacing
  private static final double WORLD_RADIUS  = 2750;   // disc radius for scattered placement

  @Override @Transactional
  public void run(ApplicationArguments args){
    if (worlds.count() > 0) return;   // already seeded — drop the DB to regenerate
    Random rnd = new Random(42);

    World w = new World(); w.setName("The Aegean — Ocean 42"); w = worlds.save(w);
    Long wid = w.getId();
    int cx = GameRules.WORLD_CENTER_X, cy = GameRules.WORLD_CENTER_Y;

    List<int[]> taken = new ArrayList<>();          // every island centre (spacing checks)
    List<double[]> groupCentres = new ArrayList<>(); // {x, y} of each group's resource island
    int nameIdx = 0, groupId = 0;

    // ---- GROUPS: 1 resource centre + 5 equidistant surrounding (GREEN, spawnable) ----
    for (int tier = 1; tier <= 3; tier++){
      int groups = GROUPS_PER_TIER[tier];
      double R = RING_RADIUS[tier];
      for (int g = 0; g < groups; g++){
        groupId++;
        double theta = (2 * Math.PI * g / groups) + tier * 0.4;     // stagger rings
        double gx = cx + Math.cos(theta) * R, gy = cy + Math.sin(theta) * R;
        groupCentres.add(new double[]{ gx, gy });

        // yellow resource island at the centre (yield strength = tier: T1<T2<T3)
        save(wid, nameIdx++, true, false, tier, groupId, tier, gx, gy, taken, rnd);

        // 5 surrounding GREEN islands, all at GROUP_RADIUS, evenly spaced
        double off = rnd.nextDouble() * 2 * Math.PI;                 // rotate the pentagon a little per group
        for (int k = 0; k < 5; k++){
          double phi = off + (2 * Math.PI * k / 5);
          save(wid, nameIdx++, false, true, tier, groupId, 0,
              gx + Math.cos(phi) * GROUP_RADIUS, gy + Math.sin(phi) * GROUP_RADIUS, taken, rnd);
        }
      }
    }

    // ---- SCATTERED loners: RED, found-only, always OUTSIDE every group's circle ----
    int placed = 0;
    for (int attempt = 0; attempt < SCATTERED * 60 && placed < SCATTERED; attempt++){
      double ang = rnd.nextDouble() * 2 * Math.PI;
      double r = 500 + rnd.nextDouble() * (WORLD_RADIUS - 500);
      double x = cx + Math.cos(ang) * r, y = cy + Math.sin(ang) * r;
      if (insideAnyGroup(x, y, groupCentres)) continue;             // never inside a group's radius
      if (!farEnough((int)x, (int)y, taken)) continue;
      int tier = r > 1750 ? 1 : r > 1050 ? 2 : 3;                   // strength band by distance (display/parity)
      save(wid, nameIdx++, false, false, tier, 0, 0, x, y, taken, rnd);
      placed++;
    }

    seedNpcs(wid, rnd);
  }

  private boolean insideAnyGroup(double x, double y, List<double[]> centres){
    for (double[] c : centres) if (Math.hypot(x - c[0], y - c[1]) < GROUP_CIRCLE) return true;
    return false;
  }
  private boolean farEnough(int px, int py, List<int[]> taken){
    for (int[] t : taken) if (Math.hypot(px - t[0], py - t[1]) < MIN_GAP) return false;
    return true;
  }

  private void save(Long wid, int nameIdx, boolean resource, boolean spawnable, int tier, int groupId,
                    int resourceLevel, double x, double y, List<int[]> taken, Random rnd){
    int px = (int)Math.round(x), py = (int)Math.round(y);
    taken.add(new int[]{px, py});
    Island is = new Island();
    is.setWorldId(wid);
    is.setName(NAMES[nameIdx % NAMES.length] + ROMAN[Math.min(ROMAN.length - 1, nameIdx / NAMES.length)]);
    is.setPx(px); is.setPy(py); is.setOceanX(42); is.setOceanY(42);
    is.setSeed(rnd.nextInt(1_000_000));
    is.setTier(tier); is.setResource(resource); is.setSpawnable(spawnable);
    is.setClusterId(groupId); is.setResourceLevel(resourceLevel);
    islands.save(is);
  }

  /** NPC rulers + barbarians on the inner tiers (red/scattered + group islands of tiers 2–3). */
  private void seedNpcs(Long wid, Random rnd){
    Alliance test = alliance(wid,"TST","Test"), red = alliance(wid,"RHD","Red Hand"), sea = alliance(wid,"SEA","Sea Wolves");
    Player[] npcs = {
        npc(wid,"Lysandros",test.getId(),420+rnd.nextInt(300)), npc(wid,"Theora",test.getId(),380+rnd.nextInt(300)),
        npc(wid,"Drakon",red.getId(),600+rnd.nextInt(400)),     npc(wid,"Kassia",red.getId(),520+rnd.nextInt(400)),
        npc(wid,"Nereus",sea.getId(),700+rnd.nextInt(400)) };

    // NPCs/barbarians sit on non-resource islands of the inner tiers (2–3), away from the spawn rim
    List<Island> inner = islands.findByWorldId(wid).stream()
        .filter(i -> !i.isResource() && i.getTier() >= 2).toList();
    if (inner.isEmpty()) return;
    Set<String> slotsTaken = new HashSet<>();
    for (Player p : npcs)
      for (int n = 0; n < 3; n++){
        int[] pos = freeSlot(inner, slotsTaken, rnd);
        cities.save(npcCity(wid, p.getId(), inner.get(pos[0]).getId(), pos[1],
            p.getUsername()+"’s Polis", 120+rnd.nextInt(220), 140+rnd.nextInt(260)));
      }
    for (int b = 0; b < inner.size(); b++){
      if (rnd.nextDouble() > 0.5) continue;
      int[] pos = freeSlot(inner, slotsTaken, rnd);
      cities.save(npcCity(wid, null, inner.get(pos[0]).getId(), pos[1],
          "Barbarian village", 80+rnd.nextInt(160), 90+rnd.nextInt(150)));
    }
    List<City> all = cities.findByWorldId(wid); Collections.shuffle(all, rnd);
    for (int i=0;i<Math.min(6, all.size());i++){
      City c = all.get(i); c.setPoints((int)Math.round(c.getPoints()*(2.4+i*0.5+rnd.nextDouble())));
      c.setPower(c.getPower()*(1.4+i*0.18)); cities.save(c);
    }
  }

  private Alliance alliance(Long wid,String tag,String name){
    Alliance a=new Alliance(); a.setWorldId(wid); a.setTag(tag); a.setName(name); return alliances.save(a);
  }
  private Player npc(Long wid,String name,Long allianceId,int combat){
    Player p=new Player(); p.setUsername(name); p.setPasswordHash("x"); p.setWorldId(wid);
    p.setAllianceId(allianceId); p.setNpc(true); p.setCombatPoints(combat); p.setCombatPointsTotal(combat); p.setLevel(2+combat/300);
    return players.save(p);
  }
  private City npcCity(Long wid, Long owner, Long islandId, int slot, String name, int points, int power){
    City c=new City(); c.setWorldId(wid); c.setPlayerId(owner); c.setIslandId(islandId); c.setSlot(slot);
    c.setName(name); c.setPoints(points); c.setPower(power);
    Race[] r = Race.values(); c.setRace(r[Math.floorMod(name.hashCode()+slot, r.length)]);
    c.setWood(power*3); c.setStone(power*3); c.setWheat(power*2);
    return c;
  }
  private int[] freeSlot(List<Island> isls, Set<String> taken, Random rnd){
    for (int tries = 0; tries < 5000; tries++){
      int i=rnd.nextInt(isls.size()); int s=rnd.nextInt(GameRules.SLOTS_PER_ISLAND); String k=i+":"+s;
      if (taken.add(k)) return new int[]{i,s};
    }
    return new int[]{0,0};
  }
}
