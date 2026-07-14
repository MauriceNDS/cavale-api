-- What the athlete uses Cavale for: running only, or running + strength.
-- Chosen at onboarding, editable in the profile; pure UI gating — the gym
-- data stays intact when disabled.
alter table users
    add column gym_enabled boolean not null default true;
