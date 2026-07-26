-- Gym revamp P2 — regrouping decided on the gym floor.
--
-- A null key cannot mean both "no opinion" and "deliberately on its own",
-- so a flag carries whether the athlete has spoken; until then the
-- program's grouping stands. Scoped to one workout, like every other
-- override here — the template is never touched.
alter table workout_block_override add column group_key varchar(4);
alter table workout_block_override add column group_overridden boolean not null default false;
