-- Live-workout deviations: "the machine is taken → do the alternative",
-- "no time left → skip this block". One row per adjusted block, scoped to
-- ONE workout — the template is never touched. Reverting to the prescribed
-- exercise and un-skipping prune the row.
create table workout_block_override (
    id                   uuid primary key default uuidv7(),
    workout_log_id       uuid        not null references workout_log (id) on delete cascade,
    template_exercise_id uuid        not null references template_exercise (id) on delete cascade,
    exercise_id          uuid references exercise (id) on delete cascade, -- replacement; null = as prescribed
    skipped              boolean     not null default false,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    constraint uq_workout_block_override unique (workout_log_id, template_exercise_id)
);
