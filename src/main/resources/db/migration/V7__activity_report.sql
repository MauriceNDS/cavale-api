-- Validation feedback + report material on activities:
-- perceived effort (5 levels), the source activity name, and downsampled
-- Strava streams (time/distance/HR/altitude/velocity) for charts.

alter table activity
    add column name varchar(200),
    add column perceived_effort varchar(15),
    add column streams_json text;
