-- Bandit camps are now per-player: each player progresses their own camp on an island,
-- instead of one shared camp whose level was split/raced between all players.

-- existing shared camps can't be attributed to a single player — drop them; they are
-- recreated per-player at level 1 on first view/attack. In-flight raids lose their camp
-- reference (resolved as "camp gone" on arrival → army simply marches home).
update movements set target_camp_id = null where target_camp_id is not null;
delete from bandit_camps;

alter table bandit_camps drop constraint bandit_camps_island_id_key;
alter table bandit_camps add column player_id bigint not null references players(id);
alter table bandit_camps add constraint uq_bandit_camp_island_player unique (island_id, player_id);
