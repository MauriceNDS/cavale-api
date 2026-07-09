-- Actual performance data for a validated session. MANUAL entries come from
-- the validation form; STRAVA entries will come from the sync (Phase 8).
-- Both paths produce the same shape → planned-vs-actual reads one table.

create table activity (
    id                 uuid primary key default uuidv7(),
    user_id            uuid        not null references users (id),
    planned_session_id uuid        not null references planned_session (id) on delete cascade,
    source             varchar(10) not null,
    date               date        not null,
    duration_min       int         not null,
    distance_km        numeric(6, 2),
    elevation_m        int,
    avg_hr             int,
    comment            text,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint activity_one_per_session unique (planned_session_id),
    constraint activity_duration_positive check (duration_min > 0)
);

create index idx_activity_user_date on activity (user_id, date);
