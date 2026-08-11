-- LTHR from a field test anchors the HR zones (Friel %LTHR beats %HRmax);
-- the Strava race flag marks true max efforts for the pace model.
ALTER TABLE users ADD COLUMN lthr smallint;
ALTER TABLE activity ADD COLUMN is_race boolean NOT NULL DEFAULT false;
