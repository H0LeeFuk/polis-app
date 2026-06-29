-- PART 2: resource system rework.
-- Base resources become Stone/Wood/Wheat (Silver removed). Each race produces ONE special
-- resource (Coal/Crystals/Iron/Pearls) for its elite units. Favor (and the Temple) are removed.

-- cities: silver -> wheat; drop favor; add the four special-resource stockpiles
alter table cities rename column silver to wheat;
alter table cities drop column favor;
alter table cities add column coal     double precision not null default 0;
alter table cities add column crystals double precision not null default 0;
alter table cities add column iron     double precision not null default 0;
alter table cities add column pearls   double precision not null default 0;

-- alliance treasury: silver node yield becomes wheat
alter table alliances rename column treasury_silver to treasury_wheat;

-- unit costs: silver -> wheat; add the elite special-resource cost
alter table unit_types rename column cost_silver to cost_wheat;
alter table unit_types add column cost_special int not null default 0;

-- buildings: Temple (favor) is gone; MINE now produces Wheat; the new EXTRACTOR yields the
-- city race's special resource. Give existing player cities a level-1 extractor.
delete from build_jobs where building_type = 'TEMPLE';
delete from city_buildings where type = 'TEMPLE';
insert into city_buildings (city_id, type, level)
  select id, 'EXTRACTOR', 1 from cities where player_id is not null
  on conflict (city_id, type) do nothing;

-- world resource nodes: Silver Vein -> Wheat Field
update resource_nodes set node_type = 'WHEAT_FIELD' where node_type = 'SILVER_VEIN';
