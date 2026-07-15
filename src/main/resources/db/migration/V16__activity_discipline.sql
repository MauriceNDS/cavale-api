-- Bicycle cross-training (P11): an activity is a run or a cross-training bike
-- session. Bikes contribute a time-based effort to training load but are
-- excluded from run-only stats and predictions. Existing rows are all runs.

ALTER TABLE activity
    ADD COLUMN discipline VARCHAR(10) NOT NULL DEFAULT 'RUN'
        CONSTRAINT activity_discipline_check CHECK (discipline IN ('RUN', 'CROSS'));
