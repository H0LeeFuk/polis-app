-- ============================================================
-- POLIS — Rise of the Aegean : initial schema
-- ============================================================

create table worlds (
  id          bigserial primary key,
  name        varchar(64) not null,
  speed       int not null default 1,
  created_at  timestamptz not null default now()
);

create table alliances (
  id          bigserial primary key,
  world_id    bigint not null references worlds(id),
  tag         varchar(8)  not null,
  name        varchar(64) not null,
  leader_id   bigint,
  created_at  timestamptz not null default now()
);

create table players (
  id            bigserial primary key,
  username      varchar(32)  not null unique,
  email         varchar(128) unique,
  password_hash varchar(100) not null,
  world_id      bigint not null references worlds(id),
  alliance_id   bigint references alliances(id),
  level         int not null default 1,
  combat_points int not null default 0,
  is_npc        boolean not null default false,
  created_at    timestamptz not null default now()
);
alter table alliances add constraint fk_alliance_leader foreign key(leader_id) references players(id);
create index idx_players_world on players(world_id);

create table islands (
  id        bigserial primary key,
  world_id  bigint not null references worlds(id),
  name      varchar(64) not null,
  ocean_x   int not null,
  ocean_y   int not null,
  px        int not null,           -- pixel position on the world canvas
  py        int not null,
  seed      bigint not null
);

create table cities (
  id            bigserial primary key,
  world_id      bigint not null references worlds(id),
  player_id     bigint references players(id),    -- NULL => barbarian / unowned
  island_id     bigint not null references islands(id),
  slot          int not null,
  name          varchar(64) not null,
  is_capital    boolean not null default false,
  god           varchar(16),
  wood          double precision not null default 0,
  stone         double precision not null default 0,
  silver        double precision not null default 0,
  favor         double precision not null default 0,
  power         double precision not null default 0,  -- defensive rating (mainly barbarians)
  points        int not null default 0,               -- cached score
  last_tick_at  timestamptz not null default now(),
  created_at    timestamptz not null default now(),
  version       bigint not null default 0,
  unique (island_id, slot)
);
create index idx_cities_player on cities(player_id);
create index idx_cities_world  on cities(world_id);

create table city_buildings (
  id       bigserial primary key,
  city_id  bigint not null references cities(id) on delete cascade,
  type     varchar(24) not null,
  level    int not null default 0,
  unique (city_id, type)
);

create table city_units (
  id       bigserial primary key,
  city_id  bigint not null references cities(id) on delete cascade,
  type     varchar(24) not null,
  count    int not null default 0,
  unique (city_id, type)
);

create table city_research (
  id       bigserial primary key,
  city_id  bigint not null references cities(id) on delete cascade,
  type     varchar(24) not null,
  unique (city_id, type)
);

-- one row per queued job; position 0 is the active job (its finish_at is set)
create table build_jobs (
  id            bigserial primary key,
  city_id       bigint not null references cities(id) on delete cascade,
  queue_type    varchar(16) not null,        -- BUILDING / BARRACKS / HARBOR
  building_type varchar(24),
  to_level      int,
  unit_type     varchar(24),
  batch         int,
  position      int not null,
  started_at    timestamptz,
  finish_at     timestamptz,
  total_seconds int not null,
  created_at    timestamptz not null default now()
);
create index idx_jobs_city   on build_jobs(city_id);
create index idx_jobs_finish on build_jobs(finish_at);

create table movements (
  id               bigserial primary key,
  world_id         bigint not null references worlds(id),
  player_id        bigint references players(id),
  source_city_id   bigint references cities(id),
  target_city_id   bigint references cities(id),
  target_island_id bigint references islands(id),
  target_slot      int,
  phase            varchar(16) not null,      -- OUT / RETURN / COLONY
  units            jsonb not null default '{}',
  loot             jsonb,
  depart_at        timestamptz not null default now(),
  arrive_at        timestamptz not null,
  resolved         boolean not null default false
);
create index idx_moves_arrive on movements(arrive_at);
