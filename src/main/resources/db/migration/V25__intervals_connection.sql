-- Per-user Intervals.icu link. The API key (Settings → Developer on
-- intervals.icu) lets Cavale publish planned workouts to the athlete's
-- Intervals.icu calendar; their Garmin link relays them to the watch.

create table intervals_connection (
    id           uuid primary key default uuidv7(),
    user_id      uuid         not null references users (id) on delete cascade,
    athlete_id   varchar(20)  not null,
    api_key      varchar(100) not null,
    last_push_at timestamptz,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    constraint intervals_connection_one_per_user unique (user_id)
);
