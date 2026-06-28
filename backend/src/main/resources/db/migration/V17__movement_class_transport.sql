-- Movement classes & transport ships. Each unit gets a movement class (land/flying/swimming)
-- that governs whether it can cross open water on its own; LAND units must be ferried by a
-- transport. Rather than rename the existing per-race rosters, we assign classes by race theme
-- and repurpose each LAND race's existing warship as its troop transport. (Names unchanged, so
-- bandit/boss/node seeds that reference unit names keep resolving.)

alter table unit_types add column movement_class    varchar(12) not null default 'LAND';
alter table unit_types add column transport_capacity int        not null default 0;

-- Race themes: Fairies fly, Newts swim, Humans & Giants march on land.
update unit_types set movement_class='FLYING'   where race='FAIRIES';
update unit_types set movement_class='SWIMMING' where race='NEWTS';
update unit_types set movement_class='LAND'     where race in ('HUMANS','GIANTS');

-- Each LAND race's flagship becomes its troop transport (a swimming ship that carries land pop).
-- Human Trireme: carries 30 pop. Giant War Barge: bigger hull, carries 60 pop (fewer, heavier Giants).
update unit_types set movement_class='SWIMMING', transport_capacity=30 where name='TRIREME';
update unit_types set movement_class='SWIMMING', transport_capacity=60 where name='WAR_BARGE';
