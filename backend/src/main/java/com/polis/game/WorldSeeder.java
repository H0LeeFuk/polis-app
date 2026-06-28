package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Seeds a single world with islands, barbarian cities, and NPC rulers + alliances on first boot. */
@Component
public class WorldSeeder implements ApplicationRunner {
  private final WorldRepo worlds; private final IslandRepo islands; private final CityRepo cities;
  private final PlayerRepo players; private final AllianceRepo alliances;

  public WorldSeeder(WorldRepo worlds, IslandRepo islands, CityRepo cities, PlayerRepo players, AllianceRepo alliances){
    this.worlds=worlds; this.islands=islands; this.cities=cities; this.players=players; this.alliances=alliances;
  }

  private static final String[] ISLAND_NAMES = {"Thalassia","Keros","Andros","Mytilos","Skarpa","Helike"};
  private static final int[][] COORDS = {{420,440},{900,250},{1250,470},{660,800},{1140,800},{230,800}};

  @Override @Transactional
  public void run(ApplicationArguments args){
    if (worlds.count() > 0) return;   // already seeded
    Random rnd = new Random(42);

    World w = new World(); w.setName("The Aegean — Ocean 42"); w = worlds.save(w);
    Long wid = w.getId();

    List<Island> isls = new ArrayList<>();
    for (int i=0;i<ISLAND_NAMES.length;i++){
      Island is = new Island(); is.setWorldId(wid); is.setName(ISLAND_NAMES[i]);
      is.setPx(COORDS[i][0]); is.setPy(COORDS[i][1]); is.setOceanX(42); is.setOceanY(42);
      is.setSeed(rnd.nextInt(1_000_000)); isls.add(islands.save(is));
    }

    // alliances + NPC rulers
    Alliance test = alliance(wid,"TST","Test"), red = alliance(wid,"RHD","Red Hand"), sea = alliance(wid,"SEA","Sea Wolves");
    Player lys = npc(wid,"Lysandros",test.getId(),420+rnd.nextInt(300));
    Player the = npc(wid,"Theora",   test.getId(),380+rnd.nextInt(300));
    Player dra = npc(wid,"Drakon",   red.getId(), 600+rnd.nextInt(400));
    Player kas = npc(wid,"Kassia",   red.getId(), 520+rnd.nextInt(400));
    Player ner = npc(wid,"Nereus",   sea.getId(), 700+rnd.nextInt(400));
    Player[] npcs = {lys,the,dra,kas,ner};

    // place 3 cities per NPC, then fill some slots with barbarians
    Set<String> taken = new HashSet<>();
    for (Player p : npcs){
      for (int n=0;n<3;n++){
        int[] pos = freeSlot(isls, taken, rnd);
        cities.save(npcCity(wid, p.getId(), isls.get(pos[0]).getId(), pos[1],
            p.getUsername()+"’s Polis", 120+rnd.nextInt(220), 140+rnd.nextInt(260)));
      }
    }
    for (Island is : isls){
      int barbs = 2 + rnd.nextInt(3);
      for (int n=0;n<barbs;n++){
        int[] pos = freeSlot(isls, taken, rnd);
        cities.save(npcCity(wid, null, isls.get(pos[0]).getId(), pos[1],
            "Barbarian village", 80+rnd.nextInt(160), 90+rnd.nextInt(150)));
      }
    }
    // boost a few cities for variety (strongholds)
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
    p.setAllianceId(allianceId); p.setNpc(true); p.setCombatPoints(combat); p.setLevel(2+combat/300);
    return players.save(p);
  }
  private City npcCity(Long wid, Long owner, Long islandId, int slot, String name, int points, int power){
    City c=new City(); c.setWorldId(wid); c.setPlayerId(owner); c.setIslandId(islandId); c.setSlot(slot);
    c.setName(name); c.setPoints(points); c.setPower(power);
    c.setWood(power*3); c.setStone(power*3); c.setSilver(power*2);
    return c;
  }
  private int[] freeSlot(List<Island> isls, Set<String> taken, Random rnd){
    while(true){ int i=rnd.nextInt(isls.size()); int s=rnd.nextInt(10); String k=i+":"+s;
      if (taken.add(k)) return new int[]{i,s}; }
  }
}
