-- Gym part, chunk 2: planned GYM sessions point to a template variant
-- ("Force Max · A" on Tuesday), and performed workouts are logged set by set.

-- The plan side: a GYM session realizes one variant. Deleting the variant
-- (or its template) keeps the session — it just loses the link.
alter table planned_session
    add column template_variant_id uuid references gym_template_variant (id) on delete set null;

-- One performed strength session. Snapshots (template_name) keep history
-- readable even after the template is renamed or deleted.
create table workout_log (
    id                  uuid primary key default uuidv7(),
    user_id             uuid        not null references users (id),
    planned_session_id  uuid unique references planned_session (id) on delete set null,
    template_variant_id uuid references gym_template_variant (id) on delete set null,
    template_name       varchar(170),
    status              varchar(12) not null, -- IN_PROGRESS | FINISHED
    started_at          timestamptz not null,
    duration_min        int,
    perceived_effort    varchar(15),
    pain_flag           boolean     not null default false,
    comment             text,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index idx_workout_log_user on workout_log (user_id, started_at desc);

-- One set actually performed: "set 2 of squats — 6 reps at 85 kg".
-- Values are snapshots; editing a template never rewrites past workouts.
create table set_log (
    id             uuid primary key default uuidv7(),
    workout_log_id uuid         not null references workout_log (id) on delete cascade,
    exercise_id    uuid         not null references exercise (id),
    exercise_name  varchar(150) not null,
    position       int          not null, -- block order inside the workout
    set_number     int          not null, -- 1-based inside the exercise
    reps           int,
    weight_kg      numeric(5, 1),
    seconds        int,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now(),
    constraint uq_set_log unique (workout_log_id, exercise_id, set_number),
    constraint set_log_effort check (reps is not null or seconds is not null)
);

-- prefill ("last time") and records ("best at 6 reps") both scan by exercise
create index idx_set_log_exercise on set_log (exercise_id, reps);
