-- The WALL building was removed from the game. Purge its rows so JPA never reads the dead enum value.
DELETE FROM public.city_buildings WHERE type = 'WALL';
DELETE FROM public.build_jobs     WHERE building_type = 'WALL';
