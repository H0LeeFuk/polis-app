create table battle_reports (
  id                       bigserial primary key,
  world_id                 bigint not null,
  movement_id              bigint,
  fought_at                timestamptz not null default now(),
  outcome                  varchar(16) not null,

  attacker_player_id       bigint,
  attacker_city_id         bigint,
  attacker_city_name       varchar(128),
  attacker_player_name     varchar(64),

  defender_player_id       bigint,
  defender_city_id         bigint,
  defender_city_name       varchar(128),
  defender_player_name     varchar(64),

  attacker_troops_sent     jsonb not null default '{}',
  attacker_troops_lost     jsonb not null default '{}',
  attacker_troops_survived jsonb not null default '{}',
  defender_troops_present  jsonb not null default '{}',
  defender_troops_lost     jsonb not null default '{}',
  defender_troops_survived jsonb not null default '{}',
  resources_stolen         jsonb not null default '{}',

  attacker_attack_power    int not null default 0,
  defender_defence_power   int not null default 0,

  attacker_read            boolean not null default false,
  defender_read            boolean not null default false,
  attacker_deleted         boolean not null default false,
  defender_deleted         boolean not null default false
);

-- list queries fan out from the two player columns, ordered by recency
create index idx_reports_attacker on battle_reports(attacker_player_id, fought_at desc);
create index idx_reports_defender on battle_reports(defender_player_id, fought_at desc);
create index idx_reports_atk_city on battle_reports(attacker_city_id);
create index idx_reports_def_city on battle_reports(defender_city_id);
