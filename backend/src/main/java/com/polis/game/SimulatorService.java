package com.polis.game;

import com.polis.domain.*;
import com.polis.repo.SpyReportRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Combat Simulator: a pure planning tool. It calls the EXACT same {@link CombatEngine#resolve} the
 * server uses for real battles, but discards all persistence — no troops deducted, no movements, no
 * rewards. Same deterministic inputs → same outcome, so the numbers it shows are exact for the
 * given forces (imported spy intel can be stale — surfaced in the UI).
 *
 * <p>Buffs are passed as explicit multipliers (race attack/defense are folded in automatically) so
 * the player can model "what if I researched X / ran this hero loadout" without a real hero/equipment
 * object. A hero is modelled as a flat attack contribution on its side.
 */
@Service
public class SimulatorService {
  private final CombatEngine combat;
  private final SpyReportRepo spyReports;

  public SimulatorService(CombatEngine combat, SpyReportRepo spyReports){
    this.combat = combat; this.spyReports = spyReports;
  }

  public record Side(String race, Map<String,Integer> troops, Double heroAttack,
                     Double attackBuff, Double defenseBuff){}
  public record SimRequest(Side attacker, Side defender, String layer){}

  private static double mul(Double v){ return v==null || v<=0 ? 1.0 : v; }
  private static Race race(String s){ try { return Race.valueOf(s); } catch (Exception e){ return Race.HUMANS; } }

  @Transactional(readOnly = true)
  public Map<String,Object> simulate(SimRequest req){
    CombatLayer layer = "SEA".equalsIgnoreCase(req.layer()) ? CombatLayer.SEA : CombatLayer.LAND;
    Side atk = req.attacker(), def = req.defender();
    Race atkRace = race(atk.race()), defRace = race(def.race());

    Map<String,Integer> atkAll = atk.troops()==null ? Map.of() : atk.troops();
    Map<String,Integer> defAll = def.troops()==null ? Map.of() : def.troops();
    Map<String,Integer> atkCombat = combat.combatants(atkAll, layer);
    Map<String,Integer> defCombat = combat.combatants(defAll, layer);

    // race auto-multipliers × explicit buff toggles from the UI
    double attackMult  = atkRace.attackMult  * mul(atk.attackBuff());
    double defenseMult = defRace.defenseMult * mul(def.defenseBuff());
    CombatEngine.Mods mods = new CombatEngine.Mods(attackMult, defenseMult, 1, 1, 1, 1, 1);

    double heroAtk = atk.heroAttack()==null ? 0 : Math.max(0, atk.heroAttack());
    CombatEngine.Result r = combat.resolve(atkCombat, atkRace.element, defCombat, mods,
        CombatEngine.CombatFx.none(), heroAtk, CombatEngine.UnitMods.none());

    Map<String,Object> out = new LinkedHashMap<>();
    out.put("layer", layer.name());
    out.put("winner", r.outcome()==BattleOutcome.VICTORY ? "ATTACKER" : "DEFENDER");
    out.put("outcome", r.outcome().name());
    out.put("globalRatio", Math.round(r.globalRatio()*1000)/1000.0);
    out.put("attackerAttackPower", r.attackerAttackPower());
    out.put("defenderDefencePower", r.defenderDefencePower());
    out.put("attacker", Map.of(
        "sent", atkCombat, "lost", r.attackerLost(), "survived", r.attackerSurvived()));
    out.put("defender", Map.of(
        "present", defCombat, "lost", r.defenderLost(), "survived", r.defenderSurvived()));
    out.put("attackByElement", r.attackByElement());
    out.put("defenseByElement", r.defenseByElement());
    return out;
  }

  /** Pre-fill a defender force from a successful spy report the caller owns. */
  @Transactional(readOnly = true)
  public Map<String,Object> importSpy(Long playerId, Long spyReportId){
    SpyReport sr = spyReports.findById(spyReportId).orElseThrow(() -> new IllegalArgumentException("Spy report not found"));
    if (!Objects.equals(sr.getOwnerPlayerId(), playerId)) throw new IllegalStateException("Not your spy report");
    if (sr.getOutcome() != SpyOutcome.SUCCESS) throw new IllegalStateException("That spy mission gathered no intel");
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("targetCityName", sr.getTargetCityName());
    out.put("troops", sr.getRevealedTroops()==null ? Map.of() : sr.getRevealedTroops());
    out.put("buildings", sr.getRevealedBuildings()==null ? Map.of() : sr.getRevealedBuildings());
    out.put("capturedAt", sr.getCapturedAt().toString());
    return out;
  }
}
