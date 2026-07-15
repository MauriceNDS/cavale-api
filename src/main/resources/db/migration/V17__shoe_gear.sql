-- Shoe / gear (P10): a per-athlete pair of shoes. Mileage is accrued by
-- linking validated activities to a shoe; a retirement threshold warns when
-- the pair is worn out. Deleting a shoe unlinks its activities (keeps history).

CREATE TABLE shoe (
    id             uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id        uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name           varchar(100) NOT NULL,
    brand          varchar(60),
    purpose        varchar(10)
        CONSTRAINT shoe_purpose_check CHECK (purpose IS NULL OR purpose IN ('ROAD', 'TRAIL', 'RACE')),
    retirement_km  int
        CONSTRAINT shoe_retirement_positive CHECK (retirement_km IS NULL OR retirement_km > 0),
    retired        boolean      NOT NULL DEFAULT false,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_shoe_user ON shoe (user_id);

ALTER TABLE activity
    ADD COLUMN shoe_id uuid REFERENCES shoe (id) ON DELETE SET NULL;

CREATE INDEX idx_activity_shoe ON activity (shoe_id) WHERE shoe_id IS NOT NULL;
