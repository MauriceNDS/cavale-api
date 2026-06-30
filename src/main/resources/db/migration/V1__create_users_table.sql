create table users (
    id            uuid primary key default uuidv7(),
    email         varchar(320) not null unique,
    password_hash varchar(100) not null,
    display_name  varchar(100) not null,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);