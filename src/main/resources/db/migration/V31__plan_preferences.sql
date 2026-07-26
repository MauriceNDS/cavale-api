-- Plan-level training preferences: how many runs and gym sessions the athlete
-- wants each week, and what a non-race block is optimizing for. Nullable —
-- the scaffold falls back to sensible defaults.
ALTER TABLE training_plan
    ADD COLUMN runs_per_week int
        CONSTRAINT training_plan_runs_check CHECK (runs_per_week BETWEEN 1 AND 7),
    ADD COLUMN gym_per_week int
        CONSTRAINT training_plan_gym_check CHECK (gym_per_week BETWEEN 0 AND 4),
    ADD COLUMN focus varchar(10)
        CONSTRAINT training_plan_focus_check CHECK (focus IN ('MAINTAIN', 'SPEED', 'ENDURANCE'));
