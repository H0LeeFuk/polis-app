-- PART 1: elemental combat. Replace the three physical defences + attack_type with four
-- elemental defences (Fire/Wind/Earth/Water), an attack_element, and an is_siege flag.

alter table unit_types add column attack_element varchar(8);
alter table unit_types add column is_siege boolean not null default false;
alter table unit_types add column defense_fire  int not null default 0;
alter table unit_types add column defense_wind  int not null default 0;
alter table unit_types add column defense_earth int not null default 0;
alter table unit_types add column defense_water int not null default 0;

-- remap the shared roster's bulk into elemental defences (preserve overall tankiness).
-- attack_element stays null for shared units → resolved from the attacking city's race element.
update unit_types set defense_fire=60, defense_wind=55, defense_earth=75, defense_water=50 where name='HOPLITE';
update unit_types set defense_fire=35, defense_wind=30, defense_earth=40, defense_water=28 where name='SWORDSMAN';
update unit_types set defense_fire=55, defense_wind=45, defense_earth=40, defense_water=45 where name='SPEARMAN';
update unit_types set defense_fire=40, defense_wind=60, defense_earth=35, defense_water=45 where name='ARCHER';
update unit_types set defense_fire=35, defense_wind=35, defense_earth=30, defense_water=30 where name='HORSEMAN';
update unit_types set defense_fire=20, defense_wind=20, defense_earth=20, defense_water=20, is_siege=true where name='CATAPULT';

alter table unit_types drop column attack_type;
alter table unit_types drop column defense_blunt;
alter table unit_types drop column defense_sharp;
alter table unit_types drop column defense_distance;

-- elite units: one per race. Attacks with the race element; costs the race's special resource.
insert into unit_types
  (name, attack, attack_element, is_siege, defense_fire, defense_wind, defense_earth, defense_water,
   speed_minutes_per_tile, carry_capacity, population_cost, kind, from_queue, train_seconds,
   cost_wood, cost_stone, cost_wheat, cost_special, research_required, race, movement_class, transport_capacity) values
  ('FLAME_LEGION',    150, 'FIRE',  false, 90, 70, 60, 45, 20, 40, 3, 'LAND',    'BARRACKS', 50, 220, 160, 120, 30, null, 'HUMANS',  'LAND',     0),
  ('EARTHSHAKER',     170, 'EARTH', false, 60, 55, 95, 70, 26, 50, 4, 'LAND',    'BARRACKS', 60, 260, 220, 140, 35, null, 'GIANTS',  'LAND',     0),
  ('STORMCALLER',     175, 'WIND',  false, 55, 95, 45, 65, 14, 35, 3, 'LAND',    'BARRACKS', 55, 240, 150, 140, 32, null, 'FAIRIES', 'FLYING',   0),
  ('LEVIATHAN_RIDER', 165, 'WATER', false, 60, 50, 65, 95, 18, 60, 4, 'LAND',    'BARRACKS', 58, 240, 170, 150, 34, null, 'NEWTS',   'SWIMMING', 0);

-- battle reports: combat composition by element (both sides)
alter table battle_reports add column attack_by_element  json;
alter table battle_reports add column defense_by_element json;
