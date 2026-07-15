-- Objective model v2 (P8 + P9): the two dials the intelligent coach needs.
--   kind      ROAD | TRAIL  — road targets are paces, trail targets are
--                            time + km-effort (km + D+/100) + D+.
--   intensity BALANCE | PERFORMANCE — BALANCE = smoother ramp / conservative
--                            guardrails; PERFORMANCE = the most aggressive
--                            ramp that still stays inside the guardrails.

ALTER TABLE objective
    ADD COLUMN kind VARCHAR(12) NOT NULL DEFAULT 'TRAIL'
        CONSTRAINT objective_kind_check CHECK (kind IN ('ROAD', 'TRAIL')),
    ADD COLUMN intensity VARCHAR(12) NOT NULL DEFAULT 'BALANCE'
        CONSTRAINT objective_intensity_check CHECK (intensity IN ('BALANCE', 'PERFORMANCE'));

-- Backfill kind from the race profile: a mostly-flat race (under 25 m of D+
-- per km, the same threshold the stats use) is road; everything else, and
-- anything without a profile, stays trail — this app's default.
UPDATE objective
   SET kind = 'ROAD'
 WHERE distance_km IS NOT NULL
   AND distance_km > 0
   AND (elevation_gain_m IS NULL OR elevation_gain_m::float / distance_km < 25);
