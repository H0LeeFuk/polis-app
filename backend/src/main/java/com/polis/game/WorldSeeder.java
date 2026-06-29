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
 * Seeds a single disc-shaped world. The Heart (centre) is reserved for the three Wonder Islands;
 * player islands ring outward in three tiers — tier 1 (core, strongest, most dangerous) nearest the
 * Heart, tier 3 (outer rim) furthest out where new players spawn. The world grows inward: outer
 * plots fill first, so the contested core stays sparse until the population pushes toward it.
 */
@Component
@Order(10)
public class WorldSeeder implements ApplicationRunner {
  private final WorldRepo worlds; private final IslandRepo islands; private final CityRepo cities;
  private final PlayerRepo players; private final AllianceRepo alliances;

  public WorldSeeder(WorldRepo worlds, IslandRepo islands, CityRepo cities, PlayerRepo players, AllianceRepo alliances){
    this.worlds=worlds; this.islands=islands; this.cities=cities; this.players=players; this.alliances=alliances;
  }

  // pools of evocative names, drawn per tier
  private static final String[] NAMES = {
      "Thalassia","Keros","Andros","Mytilos","Skarpa","Helike","Pyrgos","Lemnos","Aigai","Doria",
      "Naxara","Kymos","Elaia","Serifos","Halki","Ortygia","Patmos","Delos","Ikaros","Syros",
      "Kasos","Melos","Tenara","Zephyra","Korais","Aulis","Phaedra","Olynth"};
  // how many player islands per tier (core few, rim many) — ~1440 city plots total
  private static final int[] TIER_COUNT = { 0, 20, 40, 60 };
  private static final String[] ROMAN = { "", " II", " III", " IV", " V", " VI", " VII", " VIII" };
  /** Island half-width (largest island ~322px wide); the placement band is inset by this so bodies stay inside the rings. */
  private static final double ISLAND_MARGIN = 170;

  @Override @Transactional
  public void run(ApplicationArguments args){
    if (worlds.count() > 0) return;   // already seeded
    Random rnd = new Random(42);

    World w = new World(); w.setName("The Aegean — Ocean 42"); w = worlds.save(w);
    Long wid = w.getId();

    int cx = GameRules.WORLD_CENTER_X, cy = GameRules.WORLD_CENTER_Y;
    List<int[]> taken = new ArrayList<>();
    List<Island> isls = new ArrayList<>();
    int nameIdx = 0;

    // lay player islands on three concentric rings around the Heart. Inset the placement band by an
    // island half-width so an island's BODY (not just its centre) stays inside the tier ring lines.
    for (int tier = 1; tier <= 3; tier++){
      int count = TIER_COUNT[tier];
      double rIn = GameRules.TIER_INNER[tier] + ISLAND_MARGIN, rOut = GameRules.TIER_OUTER[tier] - ISLAND_MARGIN;
      for (int k = 0; k < count; k++){
        double baseAngle = (2 * Math.PI * k / count) + (tier * 0.5);   // stagger tiers
        int[] pos = ringSpot(cx, cy, rIn, rOut, baseAngle, taken, rnd);
        taken.add(pos);
        Island is = new Island();
        // 120 islands, 28 names → append a roman suffix on each wrap so names stay unique
        String nm = NAMES[nameIdx % NAMES.length] + ROMAN[Math.min(ROMAN.length - 1, nameIdx / NAMES.length)];
        nameIdx++;
        is.setWorldId(wid); is.setName(nm);
        is.setPx(pos[0]); is.setPy(pos[1]); is.setOceanX(42); is.setOceanY(42);
        is.setSeed(rnd.nextInt(1_000_000)); is.setTier(tier);
        isls.add(islands.save(is));
      }
    }

    // alliances + NPC rulers (seated toward the core tiers so the centre starts contested)
    Alliance test = alliance(wid,"TST","Test"), red = alliance(wid,"RHD","Red Hand"), sea = alliance(wid,"SEA","Sea Wolves");
    Player lys = npc(wid,"Lysandros",test.getId(),420+rnd.nextInt(300));
    Player the = npc(wid,"Theora",   test.getId(),380+rnd.nextInt(300));
    Player dra = npc(wid,"Drakon",   red.getId(), 600+rnd.nextInt(400));
    Player kas = npc(wid,"Kassia",   red.getId(), 520+rnd.nextInt(400));
    Player ner = npc(wid,"Nereus",   sea.getId(), 700+rnd.nextInt(400));
    Player[] npcs = {lys,the,dra,kas,ner};

    // core/mid islands for NPCs, outer rim left open for fresh players
    List<Island> inner = isls.stream().filter(i -> i.getTier() <= 2).toList();
    Set<String> slotsTaken = new HashSet<>();
    for (Player p : npcs){
      for (int n=0;n<3;n++){
        int[] pos = freeSlot(inner, slotsTaken, rnd);
        cities.save(npcCity(wid, p.getId(), inner.get(pos[0]).getId(), pos[1],
            p.getUsername()+"’s Polis", 120+rnd.nextInt(220), 140+rnd.nextInt(260)));
      }
    }
    // scatter a few barbarians across the inner tiers for early targets
    for (Island is : inner){
      int barbs = 1 + rnd.nextInt(2);
      for (int n=0;n<barbs;n++){
        int[] pos = freeSlot(inner, slotsTaken, rnd);
        cities.save(npcCity(wid, null, inner.get(pos[0]).getId(), pos[1],
            "Barbarian village", 80+rnd.nextInt(160), 90+rnd.nextInt(150)));
      }
    }
    // strongholds: boost a handful (core NPCs are the toughest)
    List<City> all = cities.findByWorldId(wid); Collections.shuffle(all, rnd);
    for (int i=0;i<Math.min(6, all.size());i++){
      City c = all.get(i); c.setPoints((int)Math.round(c.getPoints()*(2.4+i*0.5+rnd.nextDouble())));
      c.setPower(c.getPower()*(1.4+i*0.18)); cities.save(c);
    }
  }

  /** A point on the [rIn,rOut] ring near baseAngle, nudged until it clears existing islands. */
  private int[] ringSpot(int cx, int cy, double rIn, double rOut, double baseAngle, List<int[]> taken, Random rnd){
    for (int a = 0; a < 60; a++){
      double ang = baseAngle + (rnd.nextDouble()-0.5) * 0.6;
      double r = rIn + rnd.nextDouble() * (rOut - rIn);
      int px = (int)Math.round(cx + Math.cos(ang) * r);
      int py = (int)Math.round(cy + Math.sin(ang) * r);
      if (farEnough(px, py, taken)) return new int[]{px, py};
    }
    double r = (rIn + rOut) / 2;
    return new int[]{ (int)Math.round(cx + Math.cos(baseAngle)*r), (int)Math.round(cy + Math.sin(baseAngle)*r) };
  }
  private static final int MIN_GAP = 200;
  private boolean farEnough(int px, int py, List<int[]> taken){
    for (int[] t : taken) if (Math.hypot(px - t[0], py - t[1]) < MIN_GAP) return false;
    return true;
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
    while(true){ int i=rnd.nextInt(isls.size()); int s=rnd.nextInt(GameRules.SLOTS_PER_ISLAND); String k=i+":"+s;
      if (taken.add(k)) return new int[]{i,s}; }
  }
}
