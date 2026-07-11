-- Athlete hub: personal profile data, standalone Strava history (activities
-- no longer require a planned session), enrichment metrics, and best efforts
-- (distance records) extracted from Strava's per-activity analysis.

alter table users
    add column weight_kg  numeric(4, 1),
    add column height_cm  int,
    add column birth_date date,
    add column max_hr     int,
    add column resting_hr int,
    add constraint users_weight_positive check (weight_kg is null or weight_kg > 0),
    add constraint users_height_positive check (height_cm is null or height_cm > 0),
    add constraint users_hr_coherent check (
        resting_hr is null or max_hr is null or resting_hr < max_hr);

-- An activity without a session is imported history (stats corpus);
-- attaching it to a session later "validates" that session with it.
alter table activity
    alter column planned_session_id drop not null,
    add column avg_cadence_spm  numeric(5, 1),
    add column relative_effort  int,
    add column max_hr           int,
    add column records_analyzed boolean not null default false;

-- (uniqueness of external_id per Strava activity already enforced by V4's
-- idx_activity_external partial index)
create index idx_activity_user_records on activity (user_id) where external_id is not null and not records_analyzed;

-- Strava best efforts: fastest 1 km / 5 km / 10 km / semi / marathon splits
-- inside one activity. Records = min(elapsed_sec) per distance per user.
create table activity_best_effort (
    id          uuid primary key default uuidv7(),
    activity_id uuid        not null references activity (id) on delete cascade,
    user_id     uuid        not null references users (id),
    name        varchar(30) not null,
    distance_m  int         not null,
    elapsed_sec int         not null,
    date        date        not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint best_effort_unique_per_activity unique (activity_id, distance_m),
    constraint best_effort_positive check (distance_m > 0 and elapsed_sec > 0)
);

create index idx_best_effort_records on activity_best_effort (user_id, distance_m, elapsed_sec);
