-- Exact moving seconds (the minute rounding skewed the average pace by up to
-- 3 s/km against Strava) and the device laps — what the watch itself showed
-- per workout step, so per-segment stats no longer drift after a pause.
ALTER TABLE activity ADD COLUMN duration_sec integer;
ALTER TABLE activity ADD COLUMN laps_json text;
