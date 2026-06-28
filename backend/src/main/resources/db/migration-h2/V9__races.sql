-- (H2-compatible) Founding Cities & Race Selection: races replace gods, per-race rosters,
-- and the settle pending-founding marker.

alter table cities      add column race varchar(16);
update cities set race = 'HUMANS' where race is null;

alter table unit_types  add column race varchar(16);
update unit_types set race = 'HUMANS' where race is null;

alter table movements   add column arrived_at timestamp with time zone;

insert into unit_types
  (name, attack_type, attack, defense_blunt, defense_sharp, defense_distance,
   speed_minutes_per_tile, carry_capacity, population_cost, kind, from_queue, train_seconds,
   cost_wood, cost_stone, cost_silver, research_required, race) values
  ('TRIREME',        'SHARP',     90,  60,  60,  60, 20, 200,  6, 'SEA',  'HARBOR',   45, 300, 120, 180, null,       'HUMANS'),

  ('BOULDER_THROWER','DISTANCE',  90,  60,  60,  40, 40,  20,  3, 'LAND', 'BARRACKS', 35, 180, 220,  60, null,       'GIANTS'),
  ('TROLL',          'BLUNT',    120, 120,  90,  70, 40,  60,  4, 'LAND', 'BARRACKS', 45, 220, 180,  80, null,       'GIANTS'),
  ('STONE_GIANT',    'BLUNT',    180, 160, 140, 120, 45,  40,  6, 'LAND', 'BARRACKS', 60, 300, 400, 120, null,       'GIANTS'),
  ('COLOSSUS',       'SIEGE',    360, 120, 120, 120, 70,  50, 12, 'LAND', 'BARRACKS',120, 700, 900, 400, 'CATAPULT', 'GIANTS'),
  ('WAR_BARGE',      'BLUNT',    120, 120, 120, 100, 35, 300, 10, 'SEA',  'HARBOR',   70, 500, 400, 200, null,       'GIANTS'),

  ('SPRITE',         'SHARP',     45,  15,  15,  15,  8,  15,  1, 'LAND', 'BARRACKS', 12,  50,  20,  40, null,       'FAIRIES'),
  ('PIXIE_ARCHER',   'DISTANCE',  80,  12,  18,  30,  7,  20,  1, 'LAND', 'BARRACKS', 16,  70,  20,  70, null,       'FAIRIES'),
  ('GLIMMER_GUARD',  'BLUNT',     30,  45,  40,  40, 10,  10,  1, 'LAND', 'BARRACKS', 14,  60,  40,  40, null,       'FAIRIES'),
  ('MOTH_RIDER',     'SHARP',    130,  20,  20,  25,  5, 120,  3, 'LAND', 'BARRACKS', 30, 140,  60, 180, null,       'FAIRIES'),
  ('DRAGONFLY_SKIFF','DISTANCE', 100,  30,  30,  40, 10, 260,  4, 'SEA',  'HARBOR',   35, 200,  80, 260, null,       'FAIRIES'),

  ('MUDLING',        'BLUNT',     35,  50,  40,  35, 26,  15,  1, 'LAND', 'BARRACKS', 16,  60,  50,  20, null,       'NEWTS'),
  ('NEWT_SPEAR',     'SHARP',     60,  35,  70,  40, 24,  20,  1, 'LAND', 'BARRACKS', 18,  70,  60,  30, null,       'NEWTS'),
  ('SNAPPER',        'BLUNT',    140, 100, 100,  90, 14, 250,  4, 'SEA',  'HARBOR',   40, 260, 180, 140, null,       'NEWTS'),
  ('TIDE_RAIDER',    'DISTANCE', 120,  60,  60,  80, 12, 300,  3, 'SEA',  'HARBOR',   45, 220, 120, 260, null,       'NEWTS'),
  ('LEVIATHAN',      'SHARP',    300, 160, 160, 140, 12, 400, 10, 'SEA',  'HARBOR',   90, 600, 400, 500, null,       'NEWTS');
