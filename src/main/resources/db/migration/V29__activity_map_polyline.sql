-- GPS trace for the activity map (Google encoded-polyline format, ~1500 pts).
-- Kept apart from streams_json: the chart streams stay at 300 points, the map
-- needs a denser line. Backfill is deliberate: only new syncs populate it.
alter table activity
    add column map_polyline text;
