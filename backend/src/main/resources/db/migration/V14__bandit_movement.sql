-- Bandit-camp raids now march like city raids: an OUT movement carries the army to the
-- camp, the tick resolves the battle on arrival, and a RETURN movement brings survivors
-- (and looted resources) home. This column tags an OUT/RETURN movement as a camp raid.
alter table movements add column target_camp_id bigint references bandit_camps(id);
