package com.polis.api;

import com.polis.config.SecurityConfig;
import com.polis.game.SimulatorService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Combat Simulator — a pure planning tool (persists nothing, costs nothing). */
@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {
  private final SimulatorService sim;
  public SimulatorController(SimulatorService sim){ this.sim = sim; }
  private Long me(){ return SecurityConfig.currentPlayerId(); }

  @PostMapping("/combat")
  public Map<String,Object> combat(@RequestBody SimulatorService.SimRequest req){ return sim.simulate(req); }

  @GetMapping("/import-spy/{spyReportId}")
  public Map<String,Object> importSpy(@PathVariable Long spyReportId){ return sim.importSpy(me(), spyReportId); }
}
