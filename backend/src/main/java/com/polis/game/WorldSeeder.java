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
 * Seeds a single disc-shaped world by PACKING non-overlapping island GROUPS into three concentric
 * tier bands, plus a few loose islands beside each group.
 *
 * <p><b>Group</b> = 1 YELLOW resource island at the centre + 5 player islands evenly ringed around it
 * at the SAME distance (a regular pentagon). Every group is identical in shape. The 5 surrounding
 * islands are GREEN (spawnable) ONLY in Tier 1 — in Tiers 2 &amp; 3 they are RED (founding/conquest only).
 * Resource-island yield scales inward by tier (Tier 1 outer/weak → Tier 3 core/strong).
 *
 * <p><b>Packing</b>: group centres are dart-thrown within each tier's annular band so that (a) every
 * group's protective circle stays entirely inside one band — no island crosses a tier boundary — and
 * (b) no two group centres are closer than {@link #MIN_SEP} — protective radii never overlap.
 *
 * <p><b>Loose islands</b> = {@link #LOOSE_PER_GROUP} standalone RED islands placed beside each group,
 * OUTSIDE every group's radius. Players cannot spawn on these — reached only by founding/conquering.
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

  // ---- PACKING GEOMETRY (index = tier: 1 outer/weak/spawn .. 3 core/strong) ----
  // Each tier is an annular BAND [BAND_IN, BAND_OUT] measured from the world centre. A group's
  // protective circle (GROUP_CONTAIN radial half-extent) stays fully inside its band, so no island
  // ever crosses a tier boundary. Bands are sized for the disc; inner tiers are thinner (fewer groups).
  // Bands are separated by a wide CLEAR GAP (≥ ~310px between adjacent tiers' islands) so the tier
  // division line, drawn at the midpoint of that gap, never touches a 150px island body.
  private static final int[]    TARGET_GROUPS = { 0, 14, 8, 4 };       // groups to pack per tier
  private static final double[] BAND_IN       = { 0, 2260, 1350, 440 };
  private static final double[] BAND_OUT      = { 0, 2960, 2050, 1140 };
  private static final double GROUP_RADIUS  = 270;    // resource centre → each of the 5 surrounding islands
  private static final double GROUP_CONTAIN = 320;    // radial half-extent kept inside the band (≥ GROUP_RADIUS)
  private static final double MIN_SEP       = 780;    // min group-centre spacing (protective radii never overlap; clears 150px islands)
  // After groups, leftover open water is densely FILLED with loose RED islands (small gaps).
  private static final double GROUP_EXCLUDE     = GROUP_RADIUS + 90;  // keep loose out of a group's pentagon
  private static final double LOOSE_BAND_MARGIN = 80; // keep loose off the tier boundary (inside the band)
  private static final int    LOOSE_SPACING     = 220;// loose centre-to-centre min from any island (~70px water between 150px isles)

  @Override @Transactional
  public void run(ApplicationArguments args){
    if (worlds.count() > 0) return;   // already seeded — drop the DB to regenerate
    Random rnd = new Random(42);

    World w = new World(); w.setName("The Aegean — Ocean 42"); w = worlds.save(w);
    Long wid = w.getId();
    int cx = GameRules.WORLD_CENTER_X, cy = GameRules.WORLD_CENTER_Y;

    List<int[]> taken = new ArrayList<>();           // every island centre (spacing checks)
    List<double[]> groupCentres = new ArrayList<>();  // {x, y, tier} of each packed group
    int nameIdx = 0, groupId = 0;

    // ---- PACK GROUPS tier by tier: dart-throw non-overlapping centres inside each band ----
    for (int tier = 1; tier <= 3; tier++){
      double rIn = BAND_IN[tier] + GROUP_CONTAIN, rOut = BAND_OUT[tier] - GROUP_CONTAIN;
      int target = TARGET_GROUPS[tier], placedGroups = 0;
      boolean green = (tier == 1);                    // spawn only on Tier-1 surrounders
      for (int attempt = 0; attempt < target * 400 && placedGroups < target; attempt++){
        double ang = rnd.nextDouble() * 2 * Math.PI;
        double r   = rIn + rnd.nextDouble() * Math.max(0, rOut - rIn);
        double gx  = cx + Math.cos(ang) * r, gy = cy + Math.sin(ang) * r;
        if (tooCloseToGroup(gx, gy, groupCentres)) continue;   // protective radii must not overlap
        groupId++; placedGroups++;
        groupCentres.add(new double[]{ gx, gy, tier });

        // yellow resource island at the centre (yield strength = tier: T1<T2<T3)
        save(wid, nameIdx++, true, false, tier, groupId, tier, gx, gy, taken, rnd);

        // 5 surrounding islands at GROUP_RADIUS, evenly spaced — GREEN in T1, RED in T2/T3
        double off = rnd.nextDouble() * 2 * Math.PI;             // rotate the pentagon a little per group
        for (int k = 0; k < 5; k++){
          double phi = off + (2 * Math.PI * k / 5);
          save(wid, nameIdx++, false, green, tier, groupId, 0,
              gx + Math.cos(phi) * GROUP_RADIUS, gy + Math.sin(phi) * GROUP_RADIUS, taken, rnd);
        }
      }
      if (placedGroups < target)
        System.out.printf("WorldSeeder: tier %d packed %d/%d groups (band too tight)%n", tier, placedGroups, target);
    }

    // ---- FILL leftover open water with loose RED islands, packed (small gaps) ----
    // Sweep each tier band's interior and drop a found-only island wherever there is still room:
    // off the tier boundary, outside every group's pentagon, and at least LOOSE_SPACING from any
    // other island. The dart budget scales with the band's free area so pockets get saturated.
    int looseTotal = 0;
    for (int tier = 1; tier <= 3; tier++){
      double lo = BAND_IN[tier] + LOOSE_BAND_MARGIN, hi = BAND_OUT[tier] - LOOSE_BAND_MARGIN;
      double bandArea = Math.PI * (hi * hi - lo * lo);
      int attempts = (int)(bandArea / (LOOSE_SPACING * LOOSE_SPACING) * 8);   // generous — keep darting until full
      for (int a = 0; a < attempts; a++){
        double ang = rnd.nextDouble() * 2 * Math.PI;
        double r   = lo + rnd.nextDouble() * (hi - lo);
        double x   = cx + Math.cos(ang) * r, y = cy + Math.sin(ang) * r;
        if (insideAnyGroup(x, y, groupCentres)) continue;        // never inside a group's pentagon
        if (!farEnough((int) x, (int) y, taken, LOOSE_SPACING)) continue;
        save(wid, nameIdx++, false, false, tier, 0, 0, x, y, taken, rnd);
        looseTotal++;
      }
    }
    System.out.printf("WorldSeeder: packed %d groups, %d loose islands%n", groupId, looseTotal);
    validatePacking(groupCentres, cx, cy);

    seedNpcs(wid, rnd);
  }

  /** True if (x,y) is closer than MIN_SEP to any already-placed group centre. */
  private boolean tooCloseToGroup(double x, double y, List<double[]> centres){
    for (double[] c : centres) if (Math.hypot(x - c[0], y - c[1]) < MIN_SEP) return true;
    return false;
  }
  /** True if (x,y) sits within any group's radius (used to keep loose islands out of groups). */
  private boolean insideAnyGroup(double x, double y, List<double[]> centres){
    for (double[] c : centres) if (Math.hypot(x - c[0], y - c[1]) < GROUP_EXCLUDE) return true;
    return false;
  }
  private boolean farEnough(int px, int py, List<int[]> taken, int minGap){
    for (int[] t : taken) if (Math.hypot(px - t[0], py - t[1]) < minGap) return false;
    return true;
  }

  /** Hard-constraint check: no two group protective circles overlap. Logs any violation. */
  private void validatePacking(List<double[]> centres, int cx, int cy){
    int overlaps = 0;
    for (int i = 0; i < centres.size(); i++)
      for (int j = i + 1; j < centres.size(); j++){
        double[] a = centres.get(i), b = centres.get(j);
        if (Math.hypot(a[0] - b[0], a[1] - b[1]) < MIN_SEP) overlaps++;
      }
    if (overlaps > 0) System.out.printf("WorldSeeder: WARNING %d overlapping group pairs%n", overlaps);
    else System.out.println("WorldSeeder: packing OK — no overlapping groups, no boundary crossings");
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
