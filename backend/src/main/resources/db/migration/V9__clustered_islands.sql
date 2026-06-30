-- Clustered island distribution: spawnable (green/red) flag, cluster grouping, and resource-island
-- yield strength. Applies to freshly seeded worlds; existing worlds keep their rows (defaults below).
ALTER TABLE islands ADD COLUMN IF NOT EXISTS spawnable boolean NOT NULL DEFAULT false;
ALTER TABLE islands ADD COLUMN IF NOT EXISTS cluster_id integer NOT NULL DEFAULT 0;
ALTER TABLE islands ADD COLUMN IF NOT EXISTS resource_level integer NOT NULL DEFAULT 0;
