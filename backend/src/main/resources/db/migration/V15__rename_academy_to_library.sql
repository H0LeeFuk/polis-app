-- The ACADEMY building has always been surfaced to players as "the Library". This aligns the
-- persisted enum values, the mission objective, and the mission copy with that name.
update city_buildings set type='LIBRARY'                        where type='ACADEMY';
update build_jobs     set building_type='LIBRARY'               where building_type='ACADEMY';
update missions       set objective_type='REACH_LIBRARY_LEVEL'  where objective_type='REACH_ACADEMY_LEVEL';
update missions       set description=replace(description,'Academy','Library') where description like '%Academy%';
