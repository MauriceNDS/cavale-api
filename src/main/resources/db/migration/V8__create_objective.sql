-- Objectives: what a plan (season) is built around.
-- Exactly one MAIN objective per plan (the race / goal the season targets),
-- plus any number of SECONDARY objectives (intermediate races, checkpoints).

create table objective (
    id               uuid primary key default uuidv7(),
    plan_id          uuid         not null references training_plan (id) on delete cascade,
    -- denormalized from the plan owner: ownership checks without a join
    user_id          uuid         not null references users (id),
    role             varchar(10)  not null,
    type             varchar(10)  not null,
    name             varchar(150) not null,
    date             date,
    distance_km      numeric(6, 2),
    elevation_gain_m int,
    target_time_min  int,
    result_time_min  int,
    location         varchar(150),
    notes            text,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    constraint objective_role check (role in ('MAIN', 'SECONDARY')),
    constraint objective_type check (type in ('RACE', 'RECOVERY', 'FITNESS', 'GENERAL')),
    constraint objective_distance_positive check (distance_km is null or distance_km > 0),
    constraint objective_target_time_positive check (target_time_min is null or target_time_min > 0),
    constraint objective_result_time_positive check (result_time_min is null or result_time_min > 0)
);

create index idx_objective_plan on objective (plan_id);

-- The invariant "one MAIN objective per plan", guaranteed by the database.
create unique index idx_objective_one_main_per_plan on objective (plan_id) where role = 'MAIN';

-- Backfill: existing plans were created around a free-text goal; promote it
-- to a MAIN race objective dated at the end of the season (editable in-app).
insert into objective (plan_id, user_id, role, type, name, date)
select id, user_id, 'MAIN', 'RACE', coalesce(nullif(trim(goal), ''), name), end_date
from training_plan;
