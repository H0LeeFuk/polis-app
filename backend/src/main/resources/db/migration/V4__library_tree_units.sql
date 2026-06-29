-- V4: Library research-tree refactor (3 branches × 10).
-- Adds the three Library-unlocked shared units (Raider/Warden/Outrider) and the per-city state
-- columns the new signature researches need (Bloodlust streak, Blitz cooldown, City Guard cooldown).

-- ── new shared units (race = NULL → trainable by any race that researched the unlock) ──
-- Column order: id, name, attack, speed_minutes_per_tile, carry_capacity, population_cost, kind,
--   from_queue, train_seconds, cost_wood, cost_stone, cost_wheat, research_required, race,
--   movement_class, transport_capacity, cost_special, attack_element, is_siege,
--   defense_fire, defense_wind, defense_earth, defense_water, combat_layer, ship_role
INSERT INTO public.unit_types VALUES
  (31, 'RAIDER',   70, 12,  60, 1, 'LAND', 'BARRACKS', 22,  60,  20,  40, NULL, NULL, 'LAND',    0, 0, NULL, false, 30, 30, 25, 25, 'LAND', NULL),
  (32, 'WARDEN',   30, 22,  20, 1, 'LAND', 'BARRACKS', 30,  80,  90,  40, NULL, NULL, 'LAND',    0, 0, NULL, false, 75, 70, 95, 80, 'LAND', NULL),
  (33, 'OUTRIDER', 90, 10, 120, 3, 'LAND', 'BARRACKS', 35, 140,  60, 180, NULL, NULL, 'FLYING',  0, 0, NULL, false, 30, 52, 28, 34, 'LAND', NULL),
  -- MILITIA: summon-only (Bastion "City Guard"); weak defensive garrison, never trained in the queue.
  (34, 'MILITIA',  10, 30,   0, 0, 'LAND', 'BARRACKS', 9999, 0,  0,   0, NULL, NULL, 'LAND',    0, 0, NULL, false, 35, 35, 35, 35, 'LAND', NULL);

SELECT pg_catalog.setval('public.unit_types_id_seq', 34, true);

-- ── per-city Library state ──
ALTER TABLE public.cities ADD COLUMN bloodlust_stacks integer DEFAULT 0 NOT NULL;
ALTER TABLE public.cities ADD COLUMN bloodlust_last_win timestamp with time zone;
ALTER TABLE public.cities ADD COLUMN blitz_ready_at timestamp with time zone;
ALTER TABLE public.cities ADD COLUMN city_guard_ready_at timestamp with time zone;
