-- Alliance invitations + a simple per-alliance forum.

create table alliance_invites (
  id                bigserial primary key,
  alliance_id       bigint not null references alliances(id),
  player_id         bigint not null references players(id),   -- the invitee
  invited_by        bigint references players(id),
  created_at        timestamptz not null default now(),
  unique (alliance_id, player_id)
);
create index idx_invite_player on alliance_invites(player_id);

create table alliance_forum_posts (
  id                bigserial primary key,
  alliance_id       bigint not null references alliances(id),
  author_player_id  bigint not null references players(id),
  body              varchar(2000) not null,
  created_at        timestamptz not null default now()
);
create index idx_forum_alliance on alliance_forum_posts(alliance_id, created_at);
