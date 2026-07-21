-- Circuit variants: every exercise once per loop, N loops, a rest between
-- loops. While configured, per-exercise sets are ignored (one set = one loop).
alter table gym_template_variant
    add column circuit_loops    int check (circuit_loops between 1 and 10),
    add column circuit_rest_sec int check (circuit_rest_sec between 0 and 900);

-- Live-workout set-count adjustment, scoped to ONE workout: fewer sets (down
-- to none) or more than prescribed. null = as prescribed.
alter table workout_block_override
    add column sets int check (sets between 0 and 10);
