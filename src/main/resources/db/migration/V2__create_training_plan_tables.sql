-- Training plan domain: plan → weeks → sessions.
-- Shaped by the reference 21-week ultra plan (see docs/PLAN-IMPORT.md).

create table training_plan (
    id          uuid primary key default uuidv7(),
    user_id     uuid         not null references users (id),
    name        varchar(150) not null,
    goal        varchar(500),
    status      varchar(20)  not null,
    start_date  date         not null,
    end_date    date         not null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint training_plan_dates check (end_date >= start_date)
);

create index idx_training_plan_user on training_plan (user_id);

create table plan_week (
    id                 uuid primary key default uuidv7(),
    plan_id            uuid        not null references training_plan (id) on delete cascade,
    week_number        int         not null,
    start_date         date        not null,
    phase              varchar(100),
    week_type          varchar(20) not null,
    target_volume_km   numeric(5, 1),
    target_elevation_m int,
    target_load_ua     int,
    focus              text,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint plan_week_number_positive check (week_number >= 1),
    constraint plan_week_unique_number unique (plan_id, week_number)
);

create index idx_plan_week_plan on plan_week (plan_id);

create table planned_session (
    id           uuid primary key default uuidv7(),
    week_id      uuid         not null references plan_week (id) on delete cascade,
    -- user_id denormalized from the plan: the calendar query (sessions by
    -- user + date range) is the hottest read path and must not need joins.
    user_id      uuid         not null references users (id),
    date         date         not null,
    order_in_day int          not null default 0,
    discipline   varchar(10)  not null,
    title        varchar(200) not null,
    detail       text,
    zone         varchar(30),
    duration_min int,
    elevation_m  int,
    rpe_min      int,
    rpe_max      int,
    status       varchar(10)  not null default 'PLANNED',
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    constraint planned_session_rpe check (rpe_min is null or rpe_max is null or rpe_min <= rpe_max)
);

create index idx_planned_session_calendar on planned_session (user_id, date);
create index idx_planned_session_week on planned_session (week_id);
