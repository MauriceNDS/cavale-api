-- Rest is no longer an entity: a day without a session IS the rest day.
-- The calendar derives "Repos" display for empty days inside a plan week,
-- so stored REST sessions only distort session counts and adherence.
DELETE FROM planned_session WHERE discipline = 'REST';
