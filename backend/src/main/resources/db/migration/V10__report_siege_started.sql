-- Mark the battle report whose winning assault laid a siege, so the attacker can tell the
-- siege-initiating attack apart from an ordinary raid victory in their report list.
ALTER TABLE battle_reports ADD COLUMN siege_started boolean NOT NULL DEFAULT false;
