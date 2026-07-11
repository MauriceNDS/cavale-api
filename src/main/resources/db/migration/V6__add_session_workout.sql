-- First-class workout structure: a JSON tree of allure blocks and loops.
-- The description text becomes import material + consignes; the watch,
-- the UI, and the builder all read/write THIS.

alter table planned_session
    add column workout_json text;
