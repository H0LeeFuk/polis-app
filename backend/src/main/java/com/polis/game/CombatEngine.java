package com.polis.game;

import com.polis.domain.AttackType;
import com.polis.domain.BattleOutcome;
import com.polis.domain.UnitType;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Type-based combat resolver (PART 1). Each offensive unit's {@code attack} is matched
 * against the defender's matching defence ({@code BLUNT}/{@code SHARP}/{@code DISTANCE}).
 * The per-type ratios are blended into a global ratio weighted by each type's attack share;
 * {@code >= 1} means the attacker wins. SIEGE attack is tallied separately (building damage,
 * applied later) and does not count toward the troop formula.
 *
 * <p>Hero/equipment effects enter through {@link Mods}; the engine itself is pure given the
 * unit catalog, so it is reused by city raids, resource nodes and bandit camps.
 */
@Service
public class CombatEngine {
  private final UnitCatalog catalog;
  public CombatEngine(UnitCatalog catalog){ this.catalog = catalog; }

  /** Multipliers applied before resolving. Use {@link #none()} for plain combat. */
  public record Mods(double attackMult, double defenseMult, double sharpDefenseMult, double attackerLossMult){
    public static Mods none(){ return new Mods(1, 1, 1, 1); }
  }

  /**
   * Discrete hero special-effects that bend a single resolution: armour penetration per type
   * (fractions, already capped by the caller), a one-time pre-combat first-strike bonus, and a
   * loss-free opening round. Use {@link #none()} when no hero leads the army.
   */
  public record CombatFx(double armorPenBlunt, double armorPenSharp, double armorPenDistance,
                         double firstStrikePct, boolean safeRound){
    public static CombatFx none(){ return new CombatFx(0,0,0,0,false); }
  }

  public record Result(
      BattleOutcome outcome,
      Map<String,Integer> attackerLost, Map<String,Integer> attackerSurvived,
      Map<String,Integer> defenderLost, Map<String,Integer> defenderSurvived,
      double globalRatio,
      int attackerAttackPower,   // total anti-troop attack after mods
      int defenderDefencePower,  // sum of the three defence pools after mods
      int siegeDamage
  ){}

  public Result resolve(Map<String,Integer> attacker, Map<String,Integer> defender, Mods mods){
    return resolve(attacker, defender, mods, CombatFx.none());
  }

  public Result resolve(Map<String,Integer> attacker, Map<String,Integer> defender, Mods mods, CombatFx fx){
    // attacker anti-troop attack per type (+ siege tallied apart)
    EnumMap<AttackType,Double> atk = new EnumMap<>(AttackType.class);
    for (AttackType t : AttackType.values()) atk.put(t, 0.0);
    for (var e : attacker.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      UnitType u = catalog.get(e.getKey());
      atk.merge(u.getAttackType(), (double) u.getAttack()*e.getValue() * mods.attackMult(), Double::sum);
    }
    double siege = atk.get(AttackType.SIEGE);

    // defender pooled defence per type (every defender contributes to all three)
    double defB=0, defS=0, defD=0;
    for (var e : defender.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      UnitType u = catalog.get(e.getKey());
      defB += (double)u.getDefenseBlunt()*e.getValue();
      defS += (double)u.getDefenseSharp()*e.getValue();
      defD += (double)u.getDefenseDistance()*e.getValue();
    }
    defB *= mods.defenseMult(); defD *= mods.defenseMult();
    defS *= mods.defenseMult() * mods.sharpDefenseMult();
    // ARMOR_PEN: ignore a (capped) fraction of the defender's defence of each type
    defB *= (1 - clamp(fx.armorPenBlunt(),0,0.25));
    defS *= (1 - clamp(fx.armorPenSharp(),0,0.25));
    defD *= (1 - clamp(fx.armorPenDistance(),0,0.25));

    double aB=atk.get(AttackType.BLUNT), aS=atk.get(AttackType.SHARP), aD=atk.get(AttackType.DISTANCE);
    // FIRST_STRIKE: a one-time pre-combat volley boosts the army's anti-troop attack this fight
    double fs = 1 + Math.max(0, fx.firstStrikePct());
    aB*=fs; aS*=fs; aD*=fs;
    double totalAtk = aB+aS+aD;

    double globalRatio;
    if (totalAtk <= 0){
      globalRatio = 0; // only siege / no anti-troop attack → cannot beat a garrison
    } else {
      double r = aB*ratio(aB,defB) + aS*ratio(aS,defS) + aD*ratio(aD,defD);
      globalRatio = r / totalAtk;
    }

    boolean win = globalRatio >= 1.0 && totalAtk > 0;
    Map<String,Integer> aLost=new LinkedHashMap<>(), aSurv=new LinkedHashMap<>();
    Map<String,Integer> dLost=new LinkedHashMap<>(), dSurv=new LinkedHashMap<>();

    if (win){
      double aFrac = clamp(1.0/globalRatio, 0.10, 0.90) * mods.attackerLossMult();
      if (fx.safeRound()) aFrac *= 0.5;   // EXTRA_SAFE_ROUND: a loss-free opening round halves casualties
      split(attacker, clamp(aFrac,0,1), aLost, aSurv);
      split(defender, 1.0, dLost, dSurv);                 // defenders routed
    } else {
      split(attacker, 1.0, aLost, aSurv);                  // attacker wiped
      split(defender, clamp(globalRatio, 0.0, 0.90), dLost, dSurv);
    }

    return new Result(win ? BattleOutcome.VICTORY : BattleOutcome.DEFEAT,
        aLost, aSurv, dLost, dSurv, globalRatio,
        (int)Math.round(totalAtk), (int)Math.round(defB+defS+defD), (int)Math.round(siege));
  }

  private static double ratio(double a, double d){ return a / Math.max(d, 1.0); }
  private static double clamp(double v, double lo, double hi){ return Math.max(lo, Math.min(hi, v)); }

  /** Split a force into lost/survived by a fraction, per unit type. */
  private static void split(Map<String,Integer> src, double frac, Map<String,Integer> lost, Map<String,Integer> survived){
    for (var e : src.entrySet()){
      int total = e.getValue()==null?0:e.getValue();
      if (total<=0) continue;
      int l = Math.max(0, Math.min(total, (int)Math.round(total*frac)));
      if (l>0) lost.put(e.getKey(), l);
      if (total-l>0) survived.put(e.getKey(), total-l);
    }
  }
}
