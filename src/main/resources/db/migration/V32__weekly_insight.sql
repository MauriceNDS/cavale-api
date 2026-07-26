-- The weekly coach review: one insight per athlete per ISO week (prose), plus
-- the structured session-level proposals it carries. Proposals are never
-- auto-applied — the athlete approves or dismisses each one in the app.

create table weekly_insight (
    id         uuid primary key default uuidv7(),
    user_id    uuid not null references users (id) on delete cascade,
    week_start date not null,
    prose      text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, week_start)
);

create index idx_weekly_insight_user on weekly_insight (user_id, week_start desc);

create table coach_proposal (
    id          uuid primary key default uuidv7(),
    insight_id  uuid not null references weekly_insight (id) on delete cascade,
    kind        varchar(20) not null
        constraint coach_proposal_kind_check
        check (kind in ('MOVE_SESSION', 'UPDATE_SESSION', 'ADD_SESSION', 'SKIP_SESSION')),
    session_id  uuid,
    payload     text not null,
    rationale   text,
    status      varchar(10) not null default 'PENDING'
        constraint coach_proposal_status_check
        check (status in ('PENDING', 'APPLIED', 'DISMISSED')),
    resolved_at timestamptz,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index idx_coach_proposal_insight on coach_proposal (insight_id);
