package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.TradeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Marketplace + trade-convoy logistics: order book, buy/sell, delivery preview, convoys. */
@RestController
public class TradeController {
  private final TradeService trade;
  public TradeController(TradeService trade){ this.trade = trade; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  public record SellRequest(String resourceType, int bundles, int pricePerBundle){}
  public record BuyRequest(String resourceType, int bundles, int maxPricePerBundle, Long deliveryCityId){}

  /** Full marketplace panel for a city's Market: book, capacity, gold, my listings + convoys. */
  @GetMapping("/api/cities/{cityId}/trade")
  public Map<String,Object> market(@PathVariable Long cityId){ return trade.market(me(), cityId); }

  @GetMapping("/api/cities/{cityId}/trade/capacity")
  public Map<String,Object> capacity(@PathVariable Long cityId){ return trade.capacity(me(), cityId); }

  /** Buy preview: fill + delivery logistics (convoy count, ETAs, total time, gold) without committing. */
  @GetMapping("/api/cities/{cityId}/trade/buy/preview")
  public Map<String,Object> buyPreview(@PathVariable Long cityId,
                                       @RequestParam String resourceType,
                                       @RequestParam int bundles,
                                       @RequestParam int maxPricePerBundle,
                                       @RequestParam Long deliveryCityId){
    return trade.buyPreview(me(), resourceType, bundles, maxPricePerBundle, deliveryCityId);
  }

  @PostMapping("/api/cities/{cityId}/trade/buy")
  public Map<String,Object> buy(@PathVariable Long cityId, @RequestBody BuyRequest r){
    return trade.buy(me(), r.resourceType(), r.bundles(), r.maxPricePerBundle(), r.deliveryCityId());
  }

  @PostMapping("/api/cities/{cityId}/trade/sell")
  public Map<String,Object> sell(@PathVariable Long cityId, @RequestBody SellRequest r){
    return trade.sell(me(), cityId, r.resourceType(), r.bundles(), r.pricePerBundle());
  }

  @PostMapping("/api/cities/{cityId}/trade/listings/{listingId}/cancel")
  public Map<String,Object> cancel(@PathVariable Long cityId, @PathVariable Long listingId){
    trade.cancelListing(me(), listingId); return Map.of("ok", true);
  }

  /** Active trade convoys across all the player's cities (for the global movements view). */
  @GetMapping("/api/players/me/trade-convoys")
  public List<Map<String,Object>> myConvoys(){ return trade.myConvoys(me()); }

  /** Delivered-but-unseen convoys; returns them and marks them seen (drives the arrival toast). */
  @GetMapping("/api/players/me/trade-deliveries")
  public List<Map<String,Object>> deliveries(){ return trade.takeDeliveries(me()); }
}
