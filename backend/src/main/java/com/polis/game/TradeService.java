package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Player-to-player resource marketplace + trade-convoy logistics.
 *
 * <p>Sellers list resources for gold; resources are escrowed out of the source city immediately.
 * A buy fills the cheapest listings first, charges the buyer gold and pays sellers instantly, then
 * the goods physically travel to the buyer's delivery city as {@link TradeConvoy}s. Convoy carrying
 * capacity and how many can run at once come from the destination Market building level. Convoys
 * always arrive safely — this is a time + capacity mechanic only.
 */
@Service
public class TradeService {

  // --- balance knobs (tune freely) ----------------------------------------
  /** Units of a resource per tradeable bundle. */
  public static final int BUNDLE_SIZE = 100;
  /** Convoy carrying capacity (units) at market level 0. */
  public static final int BASE_CAPACITY = 500;
  /** Extra carrying capacity (units) per market level. */
  public static final int PER_LEVEL_CAPACITY = 250;
  /** Convoy pace (minutes per tile) — slower than a typical army. */
  public static final int CONVOY_MINUTES_PER_TILE = 30;
  /** Multiplier applied when the route crosses open water (abstracted trade-ship sea leg). */
  public static final double SEA_FACTOR = 1.5;

  private final CityService cityService;
  private final CityRepo cities;
  private final IslandRepo islands;
  private final PlayerRepo players;
  private final MarketListingRepo listings;
  private final TradeConvoyRepo convoys;
  private final TravelTimeService travel;

  public TradeService(CityService cityService, CityRepo cities, IslandRepo islands, PlayerRepo players,
                      MarketListingRepo listings, TradeConvoyRepo convoys, TravelTimeService travel){
    this.cityService=cityService; this.cities=cities; this.islands=islands; this.players=players;
    this.listings=listings; this.convoys=convoys; this.travel=travel;
  }

  // --- capacity curve ------------------------------------------------------

  public int convoyCapacity(int marketLevel){ return BASE_CAPACITY + marketLevel * PER_LEVEL_CAPACITY; }
  /** Higher market levels run more convoys at once (logistics throughput). */
  public int maxSimultaneousConvoys(int marketLevel){ return 2 + marketLevel / 3; }
  /** Small delivery-time bonus from a developed market (down to −40%). */
  public double speedMult(int marketLevel){ return Math.max(0.6, 1.0 - marketLevel * 0.02); }

  private int marketLevel(Long cityId){ return cityService.level(cityId, BuildingType.MARKET); }

  /** Convoy travel seconds between two cities, terrain-aware with the destination's speed bonus. */
  private long convoySeconds(City from, City to, int destLevel){
    int base = travel.seconds(from.getIslandId(), to.getIslandId(), CONVOY_MINUTES_PER_TILE);
    double s = base;
    if (travel.crossesWater(from.getIslandId(), to.getIslandId())) s *= SEA_FACTOR;
    s *= speedMult(destLevel);
    return Math.max(5, Math.round(s));
  }

  // --- helpers -------------------------------------------------------------

  private City owned(Long playerId, Long cityId){
    City c = cities.findById(cityId).orElseThrow(() -> new IllegalArgumentException("City not found"));
    if (!Objects.equals(c.getPlayerId(), playerId)) throw new IllegalStateException("Not your city");
    return cityService.sync(c);
  }
  private double resOf(City c, ResourceType rt){ return c.get(rt); }
  private void addRes(City c, ResourceType rt, long delta){ c.add(rt, delta); }
  private void creditCapped(City c, ResourceType rt, long amt, long cap){ c.set(rt, Math.min(cap, c.get(rt)+amt)); }
  private String cityName(Long id, Map<Long,String> cache){
    if (id == null) return "?";
    return cache.computeIfAbsent(id, k -> cities.findById(k).map(City::getName).orElse("Unknown city"));
  }
  private String islandNameOfCity(Long cityId, Map<Long,String> cache){
    return cache.computeIfAbsent(cityId, k -> cities.findById(k)
        .flatMap(c -> islands.findById(c.getIslandId())).map(Island::getName).orElse("?"));
  }
  private String playerName(Long id, Map<Long,String> cache){
    if (id == null) return "Unknown";
    return cache.computeIfAbsent(id, k -> players.findById(k).map(Player::getUsername).orElse("Unknown"));
  }

  // --- sell ----------------------------------------------------------------

  @Transactional
  public Map<String,Object> sell(Long playerId, Long cityId, String resType, int bundles, int pricePerBundle){
    City c = owned(playerId, cityId);
    if (bundles <= 0) throw new IllegalArgumentException("List at least one bundle");
    if (pricePerBundle <= 0) throw new IllegalArgumentException("Price must be positive");
    ResourceType rt = ResourceType.valueOf(resType);
    long units = (long) bundles * BUNDLE_SIZE;
    if (resOf(c, rt) < units)
      throw new IllegalStateException("Not enough " + rt.name().toLowerCase() + " — need " + units);
    addRes(c, rt, -units); cities.save(c);                       // escrow out of the city

    MarketListing l = new MarketListing();
    l.setWorldId(c.getWorldId()); l.setSellerPlayerId(playerId); l.setSourceCityId(cityId);
    l.setResourceType(rt); l.setBundles(bundles); l.setPricePerBundle(pricePerBundle);
    MarketListing saved = listings.save(l);
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("ok", true); out.put("listingId", saved.getId());
    out.put("escrowedUnits", units);
    return out;
  }

  @Transactional
  public void cancelListing(Long playerId, Long listingId){
    MarketListing l = listings.findById(listingId).orElseThrow(() -> new IllegalArgumentException("Listing not found"));
    if (!Objects.equals(l.getSellerPlayerId(), playerId)) throw new IllegalStateException("Not your listing");
    if (l.getStatus() != ListingStatus.ACTIVE) throw new IllegalStateException("Listing is no longer active");
    // refund the escrowed (unsold) resources back to the source city, capped at its storage
    cities.findById(l.getSourceCityId()).ifPresent(c -> {
      City synced = cityService.sync(c);
      long cap = cityService.capacity(synced.getId());
      creditCapped(synced, l.getResourceType(), (long) l.getBundles() * BUNDLE_SIZE, cap);
      cities.save(synced);
    });
    l.setStatus(ListingStatus.CANCELLED); listings.save(l);
  }

  // --- buy: fill resolution (cheapest first) -------------------------------

  private record Taken(MarketListing listing, int bundles){}
  private record Fill(List<Taken> taken, int filledBundles, long totalGold){}

  /** Resolve which active listings a buy would consume, cheapest first, without mutating anything. */
  private Fill resolveFill(Long buyerId, ResourceType rt, int wantBundles, int maxPricePerBundle){
    int need = wantBundles; long gold = 0; List<Taken> taken = new ArrayList<>();
    for (MarketListing l : listings.findByResourceTypeAndStatusOrderByPricePerBundleAscCreatedAtAsc(rt, ListingStatus.ACTIVE)){
      if (need <= 0) break;
      if (l.getPricePerBundle() > maxPricePerBundle) break;                 // sorted asc → rest are pricier
      if (Objects.equals(l.getSellerPlayerId(), buyerId)) continue;         // no buying your own goods
      int take = Math.min(need, l.getBundles());
      if (take <= 0) continue;
      taken.add(new Taken(l, take)); gold += (long) take * l.getPricePerBundle(); need -= take;
    }
    return new Fill(taken, wantBundles - need, gold);
  }

  /** Plan how a filled order splits into convoys, grouped by the seller's source city. */
  private record SourcePlan(Long sourceCityId, String sourceCity, long units, int convoys, long etaSeconds){}

  private List<SourcePlan> planConvoys(Fill f, City dest, int destLevel, int cap, Map<Long,String> nameCache){
    Map<Long,Integer> bundlesBySource = new LinkedHashMap<>();
    for (Taken t : f.taken()) bundlesBySource.merge(t.listing().getSourceCityId(), t.bundles(), Integer::sum);
    List<SourcePlan> plans = new ArrayList<>();
    for (var e : bundlesBySource.entrySet()){
      City src = cities.findById(e.getKey()).orElse(null);
      long units = (long) e.getValue() * BUNDLE_SIZE;
      int n = (int) Math.ceil(units / (double) cap);
      long secs = src == null ? 0 : convoySeconds(src, dest, destLevel);
      plans.add(new SourcePlan(e.getKey(), cityName(e.getKey(), nameCache), units, n, secs));
    }
    return plans;
  }

  @Transactional
  public Map<String,Object> buyPreview(Long playerId, String resType, int bundles, int maxPricePerBundle, Long deliveryCityId){
    City dest = owned(playerId, deliveryCityId);
    ResourceType rt = ResourceType.valueOf(resType);
    int destLevel = marketLevel(deliveryCityId);
    int cap = convoyCapacity(destLevel);
    Fill f = resolveFill(playerId, rt, bundles, maxPricePerBundle);
    Map<Long,String> nameCache = new HashMap<>();
    List<SourcePlan> plans = planConvoys(f, dest, destLevel, cap, nameCache);

    int convoyCount = plans.stream().mapToInt(SourcePlan::convoys).sum();
    long totalDelivery = plans.stream().mapToLong(SourcePlan::etaSeconds).max().orElse(0);

    List<Map<String,Object>> per = new ArrayList<>();
    for (SourcePlan p : plans){
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("sourceCity", p.sourceCity()); m.put("units", p.units());
      m.put("convoys", p.convoys()); m.put("etaSeconds", p.etaSeconds());
      per.add(m);
    }
    String splitReason = null;
    if (f.filledBundles() < bundles) splitReason = "Only " + f.filledBundles() + " of " + bundles
        + " bundles available at or below your price.";
    else if (convoyCount > plans.size()) splitReason = "Order exceeds a single convoy's capacity — split across "
        + convoyCount + " convoys.";

    Player p = players.findById(playerId).orElseThrow();
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("filledBundles", f.filledBundles());
    out.put("requestedBundles", bundles);
    out.put("totalGold", f.totalGold());
    out.put("affordable", p.getGold() >= f.totalGold());
    out.put("gold", p.getGold());
    out.put("marketLevel", destLevel);
    out.put("convoyCapacity", cap);
    out.put("maxSimultaneousConvoys", maxSimultaneousConvoys(destLevel));
    out.put("convoyCount", convoyCount);
    out.put("totalDeliveryTime", totalDelivery);
    out.put("perConvoy", per);
    out.put("splitReason", splitReason);
    return out;
  }

  @Transactional
  public Map<String,Object> buy(Long playerId, String resType, int bundles, int maxPricePerBundle, Long deliveryCityId){
    City dest = owned(playerId, deliveryCityId);
    ResourceType rt = ResourceType.valueOf(resType);
    int destLevel = marketLevel(deliveryCityId);
    int cap = convoyCapacity(destLevel);
    Fill f = resolveFill(playerId, rt, bundles, maxPricePerBundle);
    if (f.filledBundles() <= 0) throw new IllegalStateException("No listings at or below your price");

    Player buyer = players.findById(playerId).orElseThrow();
    if (buyer.getGold() < f.totalGold())
      throw new IllegalStateException("Not enough gold (need " + f.totalGold() + ", have " + buyer.getGold() + ")");

    // payment is instant: charge buyer, pay each seller, decrement listings
    buyer.setGold(buyer.getGold() - (int) f.totalGold()); players.save(buyer);
    Map<Long,Integer> bundlesBySource = new LinkedHashMap<>();
    Map<Long,Long> sellerBySource = new LinkedHashMap<>();
    for (Taken t : f.taken()){
      MarketListing l = t.listing();
      l.setBundles(l.getBundles() - t.bundles());
      if (l.getBundles() <= 0) l.setStatus(ListingStatus.FILLED);
      listings.save(l);
      players.findById(l.getSellerPlayerId()).ifPresent(s -> {
        s.setGold(s.getGold() + t.bundles() * l.getPricePerBundle()); players.save(s);
      });
      bundlesBySource.merge(l.getSourceCityId(), t.bundles(), Integer::sum);
      sellerBySource.putIfAbsent(l.getSourceCityId(), l.getSellerPlayerId());
    }

    // create convoys per source city, split into capacity-sized loads (PENDING until dispatched)
    int created = 0;
    for (var e : bundlesBySource.entrySet()){
      long remaining = (long) e.getValue() * BUNDLE_SIZE;
      while (remaining > 0){
        long load = Math.min(cap, remaining); remaining -= load;
        TradeConvoy cv = new TradeConvoy();
        cv.setWorldId(dest.getWorldId()); cv.setBuyerPlayerId(playerId);
        cv.setSellerPlayerId(sellerBySource.get(e.getKey()));
        cv.setOriginCityId(e.getKey()); cv.setDestinationCityId(deliveryCityId);
        Map<String,Long> cargo = new HashMap<>(); cargo.put(rt.name(), load); cv.setCargo(cargo);
        convoys.save(cv); created++;
      }
    }
    dispatchPending(Instant.now());   // send as many as the simultaneous-convoy limit allows

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("filledBundles", f.filledBundles());
    out.put("totalGold", f.totalGold());
    out.put("convoyCount", created);
    out.put("gold", buyer.getGold());
    return out;
  }

  // --- scheduled logistics (called from TickScheduler) ---------------------

  /** Deliver convoys that have arrived: credit cargo to the destination city. */
  @Transactional
  public void deliverDue(Instant now){
    for (TradeConvoy cv : convoys.findByStatusAndArriveAtLessThanEqual(ConvoyStatus.IN_TRANSIT, now)){
      City dst = cities.findById(cv.getDestinationCityId()).orElse(null);
      if (dst != null){
        dst = cityService.sync(dst);
        long cap = cityService.capacity(dst.getId());
        for (var e : cv.getCargo().entrySet())
          creditCapped(dst, ResourceType.valueOf(e.getKey()), e.getValue(), cap);
        cities.save(dst);
      }
      cv.setStatus(ConvoyStatus.DELIVERED); convoys.save(cv);
    }
  }

  /** Promote PENDING convoys to IN_TRANSIT while each destination is under its simultaneous limit. */
  @Transactional
  public void dispatchPending(Instant now){
    List<TradeConvoy> pending = convoys.findByStatusOrderByCreatedAtAsc(ConvoyStatus.PENDING);
    if (pending.isEmpty()) return;
    Map<Long,List<TradeConvoy>> byDest = new LinkedHashMap<>();
    for (TradeConvoy cv : pending) byDest.computeIfAbsent(cv.getDestinationCityId(), k -> new ArrayList<>()).add(cv);

    for (var entry : byDest.entrySet()){
      Long destId = entry.getKey();
      City dst = cities.findById(destId).orElse(null);
      int destLevel = marketLevel(destId);
      int max = maxSimultaneousConvoys(destLevel);
      int inTransit = (int) convoys.countByDestinationCityIdAndStatus(destId, ConvoyStatus.IN_TRANSIT);
      for (TradeConvoy cv : entry.getValue()){
        if (inTransit >= max) break;
        City src = cities.findById(cv.getOriginCityId()).orElse(null);
        if (src == null || dst == null){   // city vanished — deliver-as-noop so it doesn't wedge the queue
          cv.setStatus(ConvoyStatus.DELIVERED); cv.setSeen(true); convoys.save(cv); continue;
        }
        long secs = convoySeconds(src, dst, destLevel);
        cv.setDepartAt(now); cv.setArriveAt(now.plusSeconds(secs)); cv.setStatus(ConvoyStatus.IN_TRANSIT);
        convoys.save(cv); inTransit++;
      }
    }
  }

  // --- read views ----------------------------------------------------------

  /** Full payload for the marketplace panel of a city (book, capacity, gold, my listings, my convoys). */
  @Transactional
  public Map<String,Object> market(Long playerId, Long cityId){
    City hub = owned(playerId, cityId);
    int level = marketLevel(cityId);
    Map<Long,String> nameCache = new HashMap<>();
    Map<Long,String> islCache = new HashMap<>();
    Map<Long,String> playerCache = new HashMap<>();

    // order book: active listings grouped by resource (cheapest first)
    Map<String,List<Map<String,Object>>> book = new LinkedHashMap<>();
    for (ResourceType rt : ResourceType.values()) book.put(rt.name(), new ArrayList<>());
    for (MarketListing l : listings.findByStatusOrderByPricePerBundleAscCreatedAtAsc(ListingStatus.ACTIVE)){
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("listingId", l.getId()); m.put("pricePerBundle", l.getPricePerBundle());
      m.put("bundles", l.getBundles()); m.put("seller", playerName(l.getSellerPlayerId(), playerCache));
      m.put("mine", Objects.equals(l.getSellerPlayerId(), playerId));
      m.put("sourceCityId", l.getSourceCityId()); m.put("sourceCity", cityName(l.getSourceCityId(), nameCache));
      m.put("sourceIsland", islandNameOfCity(l.getSourceCityId(), islCache));
      book.get(l.getResourceType().name()).add(m);
    }

    // my active listings
    List<Map<String,Object>> myListings = new ArrayList<>();
    for (MarketListing l : listings.findBySellerPlayerIdAndStatus(playerId, ListingStatus.ACTIVE)){
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("listingId", l.getId()); m.put("resourceType", l.getResourceType().name());
      m.put("bundles", l.getBundles()); m.put("pricePerBundle", l.getPricePerBundle());
      m.put("sourceCity", cityName(l.getSourceCityId(), nameCache));
      myListings.add(m);
    }

    // delivery-city options
    List<Map<String,Object>> myCities = new ArrayList<>();
    for (City c : cities.findByPlayerId(playerId)){
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("id", c.getId()); m.put("name", c.getName());
      m.put("island", islands.findById(c.getIslandId()).map(Island::getName).orElse("?"));
      myCities.add(m);
    }

    List<Map<String,Object>> cvs = myConvoys(playerId);
    Player p = players.findById(playerId).orElseThrow();

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("cityId", cityId); out.put("cityName", hub.getName());
    out.put("marketLevel", level);
    out.put("convoyCapacity", convoyCapacity(level));
    out.put("maxSimultaneousConvoys", maxSimultaneousConvoys(level));
    out.put("convoySpeedMinutesPerTile", CONVOY_MINUTES_PER_TILE);
    out.put("bundleSize", BUNDLE_SIZE);
    out.put("gold", p.getGold());
    out.put("deliveryCities", myCities);
    out.put("book", book);
    out.put("myListings", myListings);
    out.put("convoys", cvs);
    return out;
  }

  @Transactional
  public Map<String,Object> capacity(Long playerId, Long cityId){
    owned(playerId, cityId);
    int level = marketLevel(cityId);
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("marketLevel", level);
    out.put("convoyCapacity", convoyCapacity(level));
    out.put("maxSimultaneousConvoys", maxSimultaneousConvoys(level));
    out.put("convoySpeedMinutesPerTile", CONVOY_MINUTES_PER_TILE);
    return out;
  }

  /** The player's active (PENDING + IN_TRANSIT) trade convoys, for the panel + movements view. */
  @Transactional(readOnly = true)
  public List<Map<String,Object>> myConvoys(Long playerId){
    Map<Long,String> nameCache = new HashMap<>();
    List<Map<String,Object>> out = new ArrayList<>();
    for (TradeConvoy cv : convoys.findByBuyerPlayerIdAndStatusIn(playerId,
        List.of(ConvoyStatus.PENDING, ConvoyStatus.IN_TRANSIT)))
      out.add(convoyView(cv, nameCache));
    return out;
  }

  private Map<String,Object> convoyView(TradeConvoy cv, Map<Long,String> nameCache){
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", cv.getId()); m.put("status", cv.getStatus().name());
    m.put("origin", cityName(cv.getOriginCityId(), nameCache));
    m.put("destination", cityName(cv.getDestinationCityId(), nameCache));
    m.put("originCityId", cv.getOriginCityId()); m.put("destinationCityId", cv.getDestinationCityId());
    m.put("cargo", cv.getCargo());
    m.put("departAt", cv.getDepartAt() == null ? null : cv.getDepartAt().toString());
    m.put("arriveAt", cv.getArriveAt() == null ? null : cv.getArriveAt().toString());
    return m;
  }

  /** Delivered convoys the buyer hasn't been notified about yet. */
  @Transactional
  public List<Map<String,Object>> takeDeliveries(Long playerId){
    Map<Long,String> nameCache = new HashMap<>();
    List<Map<String,Object>> out = new ArrayList<>();
    List<TradeConvoy> done = convoys.findByBuyerPlayerIdAndStatusAndSeenFalse(playerId, ConvoyStatus.DELIVERED);
    for (TradeConvoy cv : done){
      out.add(convoyView(cv, nameCache));
      cv.setSeen(true); convoys.save(cv);
    }
    return out;
  }
}
