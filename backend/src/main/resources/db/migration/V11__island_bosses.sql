-- Resource-island guardian bosses (seeded at runtime by NodeSeeder).
create table island_bosses (
  id              bigserial primary key,
  world_id        bigint not null,
  island_id       bigint not null unique references islands(id),
  race            varchar(16) not null,
  name            varchar(48) not null,
  level           int not null default 1,
  defender_troops jsonb not null default '{}',
  defeated_at     timestamptz,
  respawn_at      timestamptz
);
