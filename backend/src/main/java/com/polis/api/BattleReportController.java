package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.domain.BattleOutcome;
import com.polis.game.BattleReportService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Battle Reports: player-wide history, per-city history, detail, read/unread, soft delete. */
@RestController
public class BattleReportController {
  private final BattleReportService reports;

  public BattleReportController(BattleReportService reports){ this.reports = reports; }

  private Long me(){ return SecurityConfig.currentPlayerId(); }

  @GetMapping("/api/players/me/battle-reports")
  public Map<String,Object> myReports(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String outcome,
                                       @RequestParam(required = false) Boolean read,
                                       @RequestParam(required = false) Long cityId){
    return reports.list(me(), parseOutcome(outcome), read, cityId, page, size);
  }

  @GetMapping("/api/players/me/battle-reports/unread-count")
  public Map<String,Object> unreadCount(){
    return Map.of("count", reports.unreadCount(me()));
  }

  @GetMapping("/api/players/me/battle-reports/{reportId}")
  public BattleReportDTO report(@PathVariable Long reportId){
    return reports.get(me(), reportId);
  }

  @DeleteMapping("/api/players/me/battle-reports/{reportId}")
  public Map<String,Object> delete(@PathVariable Long reportId){
    reports.delete(me(), reportId);
    return Map.of("ok", true);
  }

  @PostMapping("/api/players/me/battle-reports/read-all")
  public Map<String,Object> readAll(){
    reports.markAllRead(me());
    return Map.of("ok", true);
  }

  @GetMapping("/api/cities/{cityId}/battle-reports")
  public Map<String,Object> cityReports(@PathVariable Long cityId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String outcome,
                                         @RequestParam(required = false) Boolean read){
    return reports.cityList(me(), cityId, parseOutcome(outcome), read, page, size);
  }

  private BattleOutcome parseOutcome(String s){
    if (s == null || s.isBlank()) return null;
    try { return BattleOutcome.valueOf(s.trim().toUpperCase()); }
    catch (IllegalArgumentException e){ throw new IllegalArgumentException("Invalid outcome filter"); }
  }
}
