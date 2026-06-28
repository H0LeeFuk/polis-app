create table messages (
  id             bigserial primary key,
  from_player_id bigint references players(id),
  to_player_id   bigint references players(id),
  body           varchar(1000) not null,
  sent_at        timestamptz not null default now(),
  read_flag      boolean not null default false
);
create index idx_msg_to on messages(to_player_id);
