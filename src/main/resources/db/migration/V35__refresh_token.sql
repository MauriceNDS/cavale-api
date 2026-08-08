-- Rotating refresh tokens, so a signed-in athlete stops being logged out
-- every time the 24h access token expires.
--
-- The secret itself is never stored: only its SHA-256, so a database read
-- cannot mint a session. Each use rotates — the presented token is revoked
-- and its successor recorded in replaced_by, which is what makes replay
-- detectable: a token that is already revoked being presented again means
-- the secret leaked, and the whole chain gets cut.

create table refresh_token (
    id          uuid primary key default uuidv7(),
    user_id     uuid        not null references users (id) on delete cascade,
    token_hash  varchar(64) not null unique,
    issued_at   timestamptz not null,
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    replaced_by uuid references refresh_token (id),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index idx_refresh_token_user on refresh_token (user_id);
create index idx_refresh_token_expires on refresh_token (expires_at);
