-- Persisted record of every personal access token (MCP credential) so the
-- owner can see which apps hold one and revoke them individually. The JWT
-- itself stays stateless; its jti claim links it to this row. PATs minted
-- before this table exists carry no jti and stay valid until they expire
-- (account-wide token_version remains the kill switch for those).

create table personal_token (
    id         uuid primary key default uuidv7(),
    user_id    uuid        not null references users (id) on delete cascade,
    label      varchar(100) not null,
    jti        uuid        not null unique,
    issued_at  timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_personal_token_user on personal_token (user_id);
