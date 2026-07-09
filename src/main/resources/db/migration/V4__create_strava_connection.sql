-- Per-user Strava OAuth link + provenance for imported activities.

create table strava_connection (
    id            uuid primary key default uuidv7(),
    user_id       uuid        not null references users (id) on delete cascade,
    athlete_id    bigint      not null,
    access_token  varchar(100) not null,
    refresh_token varchar(100) not null,
    expires_at    timestamptz not null,
    scope         varchar(100),
    last_sync_at  timestamptz,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint strava_connection_one_per_user unique (user_id)
);

-- Strava-sourced activities keep their origin id so re-syncs are idempotent.
alter table activity
    add column external_id bigint;

create unique index idx_activity_external on activity (external_id) where external_id is not null;
