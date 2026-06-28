-- PART 4: per-island bandit camps with 10 seeded levels.

create table bandit_camps (
  id            bigserial primary key,
  island_id     bigint not null unique,
  current_level int not null default 1,
  defeated_at   timestamptz,
  respawn_at    timestamptz,
  created_at    timestamptz not null default now()
);

create table bandit_camp_levels (
  level           int primary key,
  defender_troops jsonb not null default '{}',
  reward_type     varchar(12) not null default 'RESOURCES',
  reward_payload  jsonb not null default '{}',
  description     varchar(200)
);

-- Defenders/rewards use the seeded unit names (no naval units in this codebase, so the
-- spec's Triremes are mapped to Catapults/Horsemen). Reward payloads mix resource keys
-- (wood/stone/silver) and unit names; they are granted to the attacking city on a win.
insert into bandit_camp_levels (level, defender_troops, reward_type, reward_payload, description) values
 (1,  '{"SWORDSMAN":10}',                                        'RESOURCES','{"wood":200,"stone":100}',                  'A handful of armed deserters guard their stash.'),
 (2,  '{"SWORDSMAN":15,"ARCHER":5}',                             'RESOURCES','{"wood":300,"silver":200}',                 'Brigands with a few bowmen watch the road.'),
 (3,  '{"SWORDSMAN":20,"ARCHER":10}',                            'RESOURCES','{"stone":500,"silver":100}',                'A fortified bandit outpost.'),
 (4,  '{"SWORDSMAN":25,"ARCHER":15,"HORSEMAN":5}',               'TROOPS',   '{"ARCHER":1}',                              'Mounted raiders have joined the camp.'),
 (5,  '{"SWORDSMAN":30,"ARCHER":20,"HORSEMAN":10}',              'RESOURCES','{"wood":800,"silver":400}',                 'A war-band of seasoned marauders.'),
 (6,  '{"SWORDSMAN":20,"ARCHER":10,"HORSEMAN":5,"CATAPULT":5}',  'TROOPS',   '{"HORSEMAN":2}',                            'Siege engines loom over the palisade.'),
 (7,  '{"SWORDSMAN":25,"ARCHER":15,"CATAPULT":10}',              'RESOURCES','{"wood":1000,"stone":800,"silver":300}',    'A bandit stronghold bristling with catapults.'),
 (8,  '{"SWORDSMAN":30,"ARCHER":15,"HORSEMAN":10,"CATAPULT":15}','TROOPS',   '{"CATAPULT":3}',                            'A fortress of the bandit king''s lieutenants.'),
 (9,  '{"SWORDSMAN":40,"ARCHER":20,"HORSEMAN":20,"CATAPULT":20}','RESOURCES','{"silver":2000,"wood":1000}',               'The bandit king''s elite host.'),
 (10, '{"SWORDSMAN":40,"ARCHER":25,"HORSEMAN":20,"CATAPULT":15}','MIXED',    '{"wood":1500,"stone":1500,"silver":1500,"HORSEMAN":5}', 'The bandit king himself, with his finest warriors.');
