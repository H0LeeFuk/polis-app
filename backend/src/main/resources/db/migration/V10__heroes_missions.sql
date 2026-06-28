-- PART: Two heroes per account (Leo + Celine) + the mission system.

-- 1) Rebuild heroes without the unique(owner_player_id) and with hero_key/race/unlocked.
create table heroes_new (
  id                        bigserial primary key,
  owner_player_id           bigint not null references players(id),
  name                      varchar(32) not null,
  hero_key                  varchar(16) not null default 'LEO',
  race                      varchar(16) not null default 'HUMANS',
  unlocked                  boolean not null default true,
  level                     int not null default 1,
  current_xp                bigint not null default 0,
  xp_to_next_level          bigint not null default 100,
  unspent_attribute_points  int not null default 0,
  attr_leadership           int not null default 0,
  attr_cunning              int not null default 0,
  attr_valor                int not null default 0,
  state                     varchar(16) not null default 'IDLE',
  stationed_city_id         bigint,
  active_movement_id        bigint,
  wounded_until             timestamptz,
  unlocked_skills           jsonb not null default '[]',
  skill_cooldowns           jsonb not null default '{}',
  armed_skill               varchar(24),
  equipped_weapon_id        bigint,
  equipped_armor_id         bigint,
  equipped_amulet_id        bigint,
  created_at                timestamptz not null default now()
);
-- copy WITHOUT id so the sequence advances correctly (nothing FKs hero ids)
insert into heroes_new (owner_player_id, name, hero_key, race, unlocked, level, current_xp, xp_to_next_level,
  unspent_attribute_points, attr_leadership, attr_cunning, attr_valor, state, stationed_city_id, active_movement_id,
  wounded_until, unlocked_skills, skill_cooldowns, armed_skill, equipped_weapon_id, equipped_armor_id, equipped_amulet_id, created_at)
select owner_player_id, name, 'LEO', 'HUMANS', true, level, current_xp, xp_to_next_level,
  unspent_attribute_points, attr_leadership, attr_cunning, attr_valor, state, stationed_city_id, active_movement_id,
  wounded_until, unlocked_skills, skill_cooldowns, armed_skill, equipped_weapon_id, equipped_armor_id, equipped_amulet_id, created_at
from heroes;
drop table heroes;
alter table heroes_new rename to heroes;
create index idx_hero_active_move on heroes(active_movement_id);

-- 2) Mission config + per-player progress.
create table missions (
  id                       bigserial primary key,
  chain                    varchar(24) not null default 'STARTER',
  order_index              int not null,
  title                    varchar(80) not null,
  description              text,
  objective_type           varchar(32) not null,
  objective_target         int not null default 1,
  objective_params         jsonb not null default '{}',
  rewards                  jsonb not null default '{}',
  prerequisite_mission_id  bigint,
  unlocks_hero_key         varchar(16)
);
create table player_missions (
  id           bigserial primary key,
  player_id    bigint not null references players(id),
  mission_id   bigint not null references missions(id),
  status       varchar(16) not null default 'LOCKED',
  progress     int not null default 0,
  completed_at timestamptz,
  unique (player_id, mission_id)
);

insert into missions (chain, order_index, title, description, objective_type, objective_target, rewards, unlocks_hero_key) values
 ('STARTER',1,'First Foundations','Construct 2 buildings in your city.','BUILD_BUILDING',2,'{"wood":150,"stone":150}',null),
 ('STARTER',2,'A Growing Polis','Raise any building to level 3.','UPGRADE_BUILDING_LEVEL',3,'{"wood":200,"stone":200}',null),
 ('STARTER',3,'Raise an Army','Train 10 troops.','TRAIN_TROOPS',10,'{"silver":200}',null),
 ('STARTER',4,'Blood the Blade','Defeat a bandit camp.','ATTACK_BANDIT_CAMP',1,'{"wood":200,"stone":200,"heroXp":50}',null),
 ('STARTER',5,'Knowledge is Power','Raise your Academy to level 2.','REACH_ACADEMY_LEVEL',2,'{"silver":250}',null),
 ('STARTER',6,'The Spoils of War','Launch an attack on another player.','ATTACK_PLAYER',1,'{"silver":300,"heroXp":50}',null),
 ('STARTER',7,'Expand the Realm','Found your second city.','FOUND_CITY',1,'{"wood":300,"stone":300,"silver":200}',null),
 ('STARTER',8,'A New Ally Arrives','Complete the starter trials to recruit Celine the Fairy.','CHAIN_COMPLETE',1,'{"wood":500,"stone":500,"silver":300}','CELINE');

update missions m set prerequisite_mission_id =
  (select p.id from missions p where p.chain = m.chain and p.order_index = m.order_index - 1)
  where m.order_index > 1;
