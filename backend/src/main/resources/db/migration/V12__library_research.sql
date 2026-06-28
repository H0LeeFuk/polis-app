-- Per-city Library research tree (config lives in code: LibraryTree).
create table city_library_research (
  id           bigserial primary key,
  city_id      bigint not null references cities(id),
  research_id  varchar(32) not null,
  status       varchar(16) not null default 'RESEARCHING',
  started_at   timestamptz not null default now(),
  completes_at timestamptz,
  unique (city_id, research_id)
);
create index idx_clr_city on city_library_research(city_id);
