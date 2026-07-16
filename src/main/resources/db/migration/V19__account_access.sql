-- Admin-controlled account access, separate from athlete availability
-- (athlete_status). A brand-new account is PENDING and can reach nothing until
-- an admin activates it; an admin may DISABLE it again later. Exactly the three
-- states the admin console filters on: new / activated / deactivated.
alter table users
    add column account_status varchar(10) not null default 'PENDING',
    add column role           varchar(10) not null default 'USER';

-- Existing accounts predate the gate — keep them working so nobody currently
-- using the app gets locked out. Only accounts created from now on start life
-- PENDING (the entity default, sent explicitly by JPA on every insert).
update users set account_status = 'ACTIVE';

-- Admin account(s) are promoted on startup / at registration from
-- cavale.admin.emails — no email is hardcoded in the schema.
