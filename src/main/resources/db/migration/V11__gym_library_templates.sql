-- Gym part, chunk 1: the exercise library (which is also the theory content)
-- and the strength templates with variants and per-exercise alternatives.

-- The library: one row per exercise, personal to the athlete. An exercise
-- derived from another (slow eccentric, explosive…) points to its parent.
create table exercise (
    id              uuid primary key default uuidv7(),
    user_id         uuid         not null references users (id),
    name            varchar(150) not null,
    category        varchar(15)  not null, -- FORCE | PLIOMETRIE | GAINAGE | MOBILITE
    equipment       varchar(15)  not null, -- BARBELL | DUMBBELL | MACHINE | BODYWEIGHT | BAND | BOX
    measure         varchar(20)  not null, -- WEIGHT_REPS | BODYWEIGHT_REPS | SECONDS
    description     text,                  -- how to perform it
    resource_url    varchar(500),          -- video / article
    running_benefit text,                  -- why it matters for trail running
    derived_from_id uuid references exercise (id),
    archived        boolean      not null default false,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now()
);

-- duplicate guard: one "Squat" per athlete, case-insensitive
create unique index uq_exercise_user_name on exercise (user_id, lower(name));
create index idx_exercise_user on exercise (user_id, archived);

-- target muscles, one row per muscle (JPA @ElementCollection)
create table exercise_muscle (
    exercise_id uuid        not null references exercise (id) on delete cascade,
    muscle      varchar(15) not null,
    primary key (exercise_id, muscle)
);

-- A template is a named strength program ("Force Max"); its content lives
-- in variants (A/B/C) so alternating sessions stay one program.
create table gym_template (
    id         uuid primary key default uuidv7(),
    user_id    uuid         not null references users (id),
    name       varchar(150) not null,
    goal       text,
    archived   boolean      not null default false,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

create table gym_template_variant (
    id          uuid primary key default uuidv7(),
    template_id uuid        not null references gym_template (id) on delete cascade,
    label       varchar(10) not null, -- A, B, C…
    note        varchar(300),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint uq_variant_label unique (template_id, label)
);

-- One prescribed exercise inside a variant: 3 × 6 @ 75 %, 3 min rest.
-- reps for rep-based measures, seconds for time-based ones (gainage).
create table template_exercise (
    id            uuid primary key default uuidv7(),
    variant_id    uuid        not null references gym_template_variant (id) on delete cascade,
    exercise_id   uuid        not null references exercise (id),
    position      int         not null,
    sets          int         not null,
    reps          int,
    seconds       int,
    rest_sec      int,
    intensity_pct int,        -- % of estimated 1RM, when meaningful
    note          varchar(300),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint te_effort check (reps is not null or seconds is not null),
    constraint te_positive check (sets > 0)
);

create index idx_template_exercise_variant on template_exercise (variant_id, position);

-- Fallbacks when the machine is busy or there is no gym around, in
-- preference order.
create table template_exercise_alternative (
    id                   uuid primary key default uuidv7(),
    template_exercise_id uuid        not null references template_exercise (id) on delete cascade,
    exercise_id          uuid        not null references exercise (id),
    position             int         not null,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    constraint uq_alternative unique (template_exercise_id, exercise_id)
);
