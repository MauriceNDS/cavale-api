-- Gym revamp P1 — supersets as one unified concept, plus the fields the live
-- runner needs to propose a load and to record how a set actually felt.

-- Consecutive prescriptions sharing a key are performed in rotation. A
-- circuit is just the case where one group holds every exercise, so the
-- variant-level circuit flags below become redundant.
alter table template_exercise add column group_key varchar(4);

-- How the runner loads an exercise: the step its -/+ buttons move by, and a
-- starting weight for the very first session, before any history exists.
alter table exercise add column increment_kg numeric(4, 2);
alter table exercise add column reference_weight_kg numeric(5, 1);

-- Approach sets are excluded from every statistic; RIR says how close to
-- failure the set was, and is asked during the rest that follows it.
alter table set_log add column warmup boolean not null default false;
alter table set_log add column rir integer;

alter table set_log add constraint set_log_rir_range check (rir is null or rir between 0 and 4);

-- ── Fold existing circuits into groups ──────────────────────────────────
-- Every exercise of a circuit variant joins one group.
update template_exercise te
set group_key = 'A'
from gym_template_variant v
where te.variant_id = v.id
  and v.circuit_loops is not null;

-- A member's rest_sec already means "rest after this exercise", which inside
-- a group is the transition to the next partner. On the LAST member it is
-- instead the rest before the next round — the old circuit_rest_sec.
update template_exercise te
set rest_sec = v.circuit_rest_sec
from gym_template_variant v
where te.variant_id = v.id
  and v.circuit_loops is not null
  and v.circuit_rest_sec is not null
  and te.position = (select max(x.position) from template_exercise x where x.variant_id = v.id);

-- Rounds are now the longest member's set count. Should a circuit have asked
-- for more loops than any member had sets, raise them so no round is lost.
update template_exercise te
set sets = v.circuit_loops
from gym_template_variant v
where te.variant_id = v.id
  and v.circuit_loops is not null
  and v.circuit_loops > (select max(x.sets) from template_exercise x where x.variant_id = v.id);

alter table gym_template_variant drop column circuit_loops;
alter table gym_template_variant drop column circuit_rest_sec;
