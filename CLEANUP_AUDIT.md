# POLIS — Codebase Cleanup Audit (Phase 1)

Scope: behavior-preserving cleanup only (Phases 1–3). Phase 4 (behavior-touching
refactors) is **deferred to recommendations** because there is no automated test net.

## Baseline (before any change)

| Check | Backend (Java 21 / Spring Boot) | Frontend (React / TS / Vite) |
|---|---|---|
| Unit tests | **None** (no `src/test`) | **None** (no test script/files) |
| Lint | None configured | No ESLint config |
| Compile / typecheck | ✅ compiles (app boots) | ✅ `tsc --noEmit` clean |
| Build | ✅ `mvn` | ✅ `tsc -b && vite build` |

Verification gate used per phase (agreed): **compile + typecheck + build + manual smoke**.

## Findings

### Backend — clean overall
- **Unused imports (3):** `CityFactory.java` (`java.util.List`), `ProgressionService.java`
  (`java.time.Duration`, `java.time.Instant`). → Phase 2.
- **`System.out` prints (4):** all in `WorldSeeder.java` (world-gen diagnostics). A SLF4J
  `Logger` is already the norm in the service layer. → Phase 3 (convert to logger).
- **Dependency injection:** ✅ constructor injection everywhere (`@Autowired` field injection: 0).
- **Empty catch blocks:** ✅ none.
- **`catch (Exception)` (9):** all intentional parse-guards (enum `valueOf` fallbacks,
  JWT/`GameRules` defaults) — none silently hide bugs. Narrowing types = **Phase 4 (deferred)**.
- **TODO/FIXME:** none.

### Frontend
- **Unused imports / locals / params (27, via `tsc --noUnusedLocals --noUnusedParameters`):**
  `buildings.ts` (4× unused param `s`), `BattleReports.tsx` (`ELEMENT_GLYPH`, `ELEMENT_ORDER`),
  `FoundCity.tsx` (`setStep`), `Game.tsx` (`RACES`, `constructionSvg`, `emptyPlotSvg`,
  `UnitTooltip`, `glow`, `MOVE_BADGE`, `MOVE_LABEL`, `moveCount`, `hostileInbound`, `onFound`,
  `b`, `Queue`, `phaseLabel`, `etaLabel`), `HeroPanel.tsx` (`cities`),
  `MovementsOverview.tsx` (`titleCase`), `SimulatorPanel.tsx` (`sumT`), `TradePanel.tsx`
  (`useRef`), `WondersPanel.tsx` (`now`), `movements.tsx` (`useRef`). → Phase 2.
  (Several are leftovers from the recent Barracks-modal refactor.)
- **`console.*`:** ✅ 0.
- **Dead files:** ✅ none (every module is imported).
- **Unused dependencies:** ✅ none (`react`, `react-dom` both used).
- **`localStorage`:** legitimate (auth token in `api.ts`, map prefs in `mapMovementPrefs.ts`,
  wrapped in try/catch). App does not forbid storage — **keep**.
- **`any` / `as any` / `@ts-ignore` (40):** typing debt. Tightening types can change
  inference/behavior at the edges → **Phase 4 (deferred)**.

## Phase plan
- **Phase 2 (safe):** delete the 3 backend imports; delete the 27 frontend dead
  imports/locals/functions (rename unused *positional callback params* to `_` rather than
  drop, to preserve arity).
- **Phase 3 (safe):** convert `WorldSeeder` `System.out` → SLF4J logger; no other lint tooling
  to run.

## Phase 4 — deferred recommendations (NOT applied)
Prioritized, for separate approval (each risks behavior without a test net):
1. **Add a test harness** (JUnit + MockMvc backend; Vitest + RTL frontend) — prerequisite that
   makes everything below safe. *High value.*
2. **Narrow `catch (Exception)`** to `IllegalArgumentException`/`NullPointerException` on the 9
   enum/parse guards. *Low risk, low value.*
3. **Reduce `any` (40 sites)** with real types. *Medium.*
4. **Split oversized `Game.tsx`** (~1500 lines, many sub-components) into per-feature files. *Medium.*
5. **Introduce an ESLint + Prettier config** (frontend) and Spotless/Checkstyle (backend) to keep
   this from regressing. *Medium.*
6. **Audit `@Transactional` boundaries** on the scheduled resolvers for read-vs-write correctness. *Review-only.*
7. **N+1 query review** in list/DTO builders (e.g. ranking, world view). *Review-only.*
