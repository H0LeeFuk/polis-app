-- Hero equipment: 3 slots (Weapon/Armor/Amulet) → 4 slots (Weapon/Armor/Relic/Pet),
-- add LEGENDARY rarity (no schema change — varchar) and discrete special effects.

-- heroes: rename amulet slot to relic, add pet slot
alter table heroes rename column equipped_amulet_id to equipped_relic_id;
alter table heroes add column equipped_pet_id bigint;

-- hero_items: migrate AMULET items to RELIC, add special_effects list
update hero_items set slot = 'RELIC' where slot = 'AMULET';
alter table hero_items add column special_effects jsonb not null default '[]';
