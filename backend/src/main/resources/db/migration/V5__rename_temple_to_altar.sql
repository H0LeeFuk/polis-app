-- Rename the TEMPLE building type to ALTAR everywhere it is persisted as an enum-name string.
-- The enum value BuildingType.TEMPLE was renamed to BuildingType.ALTAR in code; existing rows
-- still hold the old name and would fail to map on read. Backfill them.
UPDATE public.city_buildings SET type = 'ALTAR' WHERE type = 'TEMPLE';
UPDATE public.build_jobs     SET building_type = 'ALTAR' WHERE building_type = 'TEMPLE';
