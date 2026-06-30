-- Track where away troops came from so recalls/withdrawals/dismissals return them to their ORIGIN
-- city (not the player's capital). Reinforcements become keyed by (host, owner, origin); resource
-- nodes remember the city that claimed them. Legacy rows keep NULL → fall back to the capital.
ALTER TABLE reinforcements ADD COLUMN IF NOT EXISTS origin_city_id bigint;
ALTER TABLE resource_nodes  ADD COLUMN IF NOT EXISTS origin_city_id bigint;
