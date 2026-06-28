-- PART 2: the account hero, plus hero/siege fields on battle reports.

create table heroes (
  id                        bigserial primary key,
  owner_player_id           bigint not null unique references players(id),
  name                      varchar(32) not null,
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
create index idx_hero_active_move on heroes(active_movement_id);

-- battle reports gain siege damage + a hero participation summary
alter table battle_reports add column siege_damage           int not null default 0;
alter table battle_reports add column hero_name              varchar(32);
alter table battle_reports add column hero_level             int not null default 0;
alter table battle_reports add column hero_attack_bonus_pct  int not null default 0;
alter table battle_reports add column hero_loss_reduction_pct int not null default 0;
alter table battle_reports add column hero_skill_used        varchar(24);
alter table battle_reports add column hero_xp_gained         int not null default 0;
alter table battle_reports add column hero_leveled_to        int;
alter table battle_reports add column hero_wounded           boolean not null default false;
