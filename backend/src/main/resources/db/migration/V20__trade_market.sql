-- Trade logistics: a player-to-player resource marketplace where sold goods physically travel
-- to the buyer as timed trade convoys. Payment is instant (gold); only the goods take time.

create table market_listings (
  id               bigserial primary key,
  world_id         bigint not null references worlds(id),
  seller_player_id bigint not null references players(id),
  source_city_id   bigint not null references cities(id),
  resource_type    varchar(8)  not null,           -- WOOD / STONE / SILVER
  bundles          int         not null,           -- remaining bundles (1 bundle = BUNDLE_SIZE units)
  price_per_bundle int         not null,           -- gold per bundle
  status           varchar(12) not null default 'ACTIVE',  -- ACTIVE / FILLED / CANCELLED
  created_at       timestamptz not null default now()
);
create index idx_listings_book on market_listings(resource_type, status, price_per_bundle);
create index idx_listings_seller on market_listings(seller_player_id, status);

create table trade_convoys (
  id                  bigserial primary key,
  world_id            bigint not null references worlds(id),
  buyer_player_id     bigint not null references players(id),
  seller_player_id    bigint references players(id),
  origin_city_id      bigint not null references cities(id),
  destination_city_id bigint not null references cities(id),
  cargo               jsonb  not null default '{}',         -- { resourceType: quantity }
  status              varchar(12) not null default 'PENDING',  -- PENDING / IN_TRANSIT / DELIVERED
  depart_at           timestamptz,
  arrive_at           timestamptz,
  seen                boolean not null default false,
  created_at          timestamptz not null default now()
);
create index idx_convoy_due on trade_convoys(status, arrive_at);
create index idx_convoy_dest on trade_convoys(destination_city_id, status);
create index idx_convoy_buyer on trade_convoys(buyer_player_id, status);
