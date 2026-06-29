package com.polis.game;

import com.polis.domain.BattleOutcome;
import com.polis.domain.CombatLayer;
import com.polis.domain.Element;
import com.polis.domain.ShipRole;
import com.polis.domain.UnitType;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Elemental combat resolver (PART 1, Option B — multi-elemental defence, NO counter table).
 * Attackers are grouped by their element (FIRE/WIND/EARTH/WATER); each defender contributes its
 * resistance to ALL four elements. Per-element ratios are blended into a global ratio weighted by
 * each element's share of total attack; {@code >= 1} means the attacker wins. SIEGE attack is
 * tallied separately (building damage, applied later) and does not count toward the troop formula.
 *
 * <p>A unit's element is its own {@code attackElement}, falling back to the attacking city's race
 * element for shared units. Hero/Library effects enter through {@link Mods}; the engine is pure
 * given the unit catalog, so it is reused by city raids, resource nodes and bandit camps.
 */
@Service
public class CombatEngine {
  private final UnitCatalog catalog;
  public CombatEngine(UnitCatalog catalog){ this.catalog = catalog; }

  // ---- two-layer combat (sea fleets vs land garrison; the layers never cross-damage) ----------
  // Transports only carry — they are never combatants (don't fight, aren't destroyed in battle).

  /** "SEA", "LAND", "MIXED", or null (no combatants) — non-throwing, for the dispatch preview. */
  public String layerLabel(Map<String,Integer> army){
    boolean land=false, sea=false;
    if (army != null) for (var e : army.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      UnitType u = catalog.get(e.getKey());
      if (u.getShipRole()==ShipRole.TRANSPORT) continue;   // cargo, not a combatant
      if (u.isSea()) sea=true; else land=true;
    }
    if (land && sea) return "MIXED";
    return sea ? "SEA" : land ? "LAND" : null;
  }

  /** Layer of an army's COMBATANT units; null if it has none. Throws if SEA and LAND are mixed. */
  public CombatLayer attackLayer(Map<String,Integer> army){
    String l = layerLabel(army);
    if ("MIXED".equals(l)) throw new IllegalStateException(
      "Send ships and ground troops as separate attacks — a sea attack hits the enemy fleet, a land attack the garrison.");
    return "SEA".equals(l) ? CombatLayer.SEA : "LAND".equals(l) ? CombatLayer.LAND : null;
  }

  /** Filter a unit map to the COMBATANTS on the given layer (transports always excluded). */
  public Map<String,Integer> combatants(Map<String,Integer> units, CombatLayer layer){
    Map<String,Integer> m = new LinkedHashMap<>();
    if (units==null || layer==null) return m;
    for (var e : units.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      UnitType u = catalog.get(e.getKey());
      if (u.getShipRole()==ShipRole.TRANSPORT) continue;
      if (u.isSea() == (layer==CombatLayer.SEA)) m.put(e.getKey(), e.getValue());
    }
    return m;
  }

  /**
   * Multipliers applied before resolving. {@code defenseMult} scales all four elemental defences;
   * the per-element mults add an extra lean (Library wards, hero PHALANX, element-specific items).
   */
  public record Mods(double attackMult, double defenseMult,
                     double defFireMult, double defWindMult, double defEarthMult, double defWaterMult,
                     double attackerLossMult){
    public static Mods none(){ return new Mods(1, 1, 1, 1, 1, 1, 1); }
    public double defMult(Element e){
      return switch (e){ case FIRE -> defFireMult; case WIND -> defWindMult; case EARTH -> defEarthMult; case WATER -> defWaterMult; };
    }
  }

  /**
   * Discrete hero special-effects that bend a single resolution: armour penetration per element
   * (fractions, already capped by the caller), a one-time pre-combat first-strike bonus, and a
   * loss-free opening round. Use {@link #none()} when no hero leads the army.
   */
  public record CombatFx(double apFire, double apWind, double apEarth, double apWater,
                         double firstStrikePct, boolean safeRound){
    public static CombatFx none(){ return new CombatFx(0,0,0,0,0,false); }
    public double armorPen(Element e){
      return switch (e){ case FIRE -> apFire; case WIND -> apWind; case EARTH -> apEarth; case WATER -> apWater; };
    }
  }

  /**
   * Per-unit-type Library upgrades and a siege multiplier. {@code atk}/{@code def} are keyed by
   * UPPERCASE unit name (1.0 = unchanged) so a research can buff only its own unit (Honed Raiders,
   * Tempered Wardens, Pack Instincts); {@code siegeMult} scales siege/wall damage (Breach Engines,
   * Siegebreaker). Use {@link #none()} when no per-unit modifier applies.
   */
  public record UnitMods(Map<String,Double> atk, Map<String,Double> def, double siegeMult){
    public static UnitMods none(){ return new UnitMods(Map.of(), Map.of(), 1.0); }
    double atkOf(String name){ return atk.getOrDefault(name.toUpperCase(), 1.0); }
    double defOf(String name){ return def.getOrDefault(name.toUpperCase(), 1.0); }
  }

  public record Result(
      BattleOutcome outcome,
      Map<String,Integer> attackerLost, Map<String,Integer> attackerSurvived,
      Map<String,Integer> defenderLost, Map<String,Integer> defenderSurvived,
      double globalRatio,
      int attackerAttackPower,   // total anti-troop attack after mods
      int defenderDefencePower,  // sum of the four defence pools after mods
      int siegeDamage,
      Map<String,Integer> attackByElement,   // FIRE/WIND/EARTH/WATER -> attack power (for reports)
      Map<String,Integer> defenseByElement   // FIRE/WIND/EARTH/WATER -> pooled defence (for reports)
  ){}

  public Result resolve(Map<String,Integer> attacker, Element attackerElement,
                        Map<String,Integer> defender, Mods mods){
    return resolve(attacker, attackerElement, defender, mods, CombatFx.none(), 0);
  }

  public Result resolve(Map<String,Integer> attacker, Element attackerElement,
                        Map<String,Integer> defender, Mods mods, CombatFx fx){
    return resolve(attacker, attackerElement, defender, mods, fx, 0);
  }

  public Result resolve(Map<String,Integer> attacker, Element attackerElement,
                        Map<String,Integer> defender, Mods mods, CombatFx fx, double heroAttack){
    return resolve(attacker, attackerElement, defender, mods, fx, heroAttack, UnitMods.none());
  }

  /**
   * @param heroAttack flat anti-troop attack the leading hero contributes in {@code attackerElement}
   *                   (lets a hero fight — and march alone). Pass 0 when no hero leads.
   * @param um per-unit Library upgrades + siege multiplier (Honed Raiders, Tempered Wardens, etc.).
   */
  public Result resolve(Map<String,Integer> attacker, Element attackerElement,
                        Map<String,Integer> defender, Mods mods, CombatFx fx, double heroAttack, UnitMods um){
    Element fallback = attackerElement != null ? attackerElement : Element.FIRE;
    // attacker anti-troop attack per element (+ siege tallied apart)
    EnumMap<Element,Double> atk = new EnumMap<>(Element.class);
    for (Element e : Element.values()) atk.put(e, 0.0);
    double siege = 0;
    for (var e : attacker.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      UnitType u = catalog.get(e.getKey());
      double power = (double)u.getAttack()*e.getValue() * mods.attackMult() * um.atkOf(e.getKey());
      if (u.isSiege()){ siege += power * um.siegeMult(); continue; }   // Breach Engines / Siegebreaker
      Element el = u.getAttackElement()!=null ? u.getAttackElement() : fallback;
      atk.merge(el, power, Double::sum);
    }
    // hero's own attack joins the army in the attacker's element (already buffed by mods)
    if (heroAttack > 0) atk.merge(fallback, heroAttack * mods.attackMult(), Double::sum);

    // defender pooled defence per element (every defender contributes to all four)
    EnumMap<Element,Double> def = new EnumMap<>(Element.class);
    for (Element e : Element.values()) def.put(e, 0.0);
    for (var e : defender.entrySet()){
      if (e.getValue()==null || e.getValue()<=0) continue;
      UnitType u = catalog.get(e.getKey());
      double um2 = um.defOf(e.getKey());   // Tempered Wardens / Honed Raiders defensive upgrade
      for (Element el : Element.values()) def.merge(el, (double)u.defenseOf(el)*e.getValue()*um2, Double::sum);
    }
    // base mult → per-element lean → armour-pen (capped fraction ignored)
    for (Element el : Element.values()){
      double d = def.get(el) * mods.defenseMult() * mods.defMult(el);
      d *= (1 - clamp(fx.armorPen(el), 0, 0.25));
      def.put(el, d);
    }

    // FIRST_STRIKE: a one-time pre-combat volley boosts the army's anti-troop attack this fight
    double fs = 1 + Math.max(0, fx.firstStrikePct());
    double totalAtk = 0;
    for (Element el : Element.values()){ atk.put(el, atk.get(el)*fs); totalAtk += atk.get(el); }

    double globalRatio;
    if (totalAtk <= 0){
      globalRatio = 0; // only siege / no anti-troop attack → cannot beat a garrison
    } else {
      double r = 0;
      for (Element el : Element.values()){
        double a = atk.get(el);
        if (a > 0) r += a * ratio(a, def.get(el));
      }
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

    double defTotal = 0; Map<String,Integer> atkByEl=new LinkedHashMap<>(), defByEl=new LinkedHashMap<>();
    for (Element el : Element.values()){
      defTotal += def.get(el);
      atkByEl.put(el.name(), (int)Math.round(atk.get(el)));
      defByEl.put(el.name(), (int)Math.round(def.get(el)));
    }

    return new Result(win ? BattleOutcome.VICTORY : BattleOutcome.DEFEAT,
        aLost, aSurv, dLost, dSurv, globalRatio,
        (int)Math.round(totalAtk), (int)Math.round(defTotal), (int)Math.round(siege),
        atkByEl, defByEl);
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
