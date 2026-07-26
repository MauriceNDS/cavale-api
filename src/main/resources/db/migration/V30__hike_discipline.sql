-- Treks become first-class: HIKE joins the activity disciplines so Strava
-- hikes can be ingested (they feed overall load but never run-only stats).
ALTER TABLE activity
    DROP CONSTRAINT activity_discipline_check;
ALTER TABLE activity
    ADD CONSTRAINT activity_discipline_check CHECK (discipline IN ('RUN', 'CROSS', 'HIKE'));
