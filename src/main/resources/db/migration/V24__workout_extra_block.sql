-- Mid-workout additions: an exercise done on top of the programmed variant
-- ("I had time for calf raises"), scoped to ONE workout — the template is
-- never touched. Its sets land in set_log like any other block's.
create table workout_extra_block (
    id             uuid primary key default uuidv7(),
    workout_log_id uuid        not null references workout_log (id) on delete cascade,
    exercise_id    uuid        not null references exercise (id) on delete cascade,
    position       int         not null,
    sets           int         not null,
    reps           int,
    seconds        int,
    rest_sec       int,
    note           varchar(300),
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    constraint uq_workout_extra_block unique (workout_log_id, exercise_id)
);
