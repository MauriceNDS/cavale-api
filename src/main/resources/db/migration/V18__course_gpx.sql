-- GPX course planner (P12): a race course parsed from a GPX file, attached to
-- an objective (one course per objective). The downsampled elevation profile
-- is stored so pacing never re-parses; aid stations / points of interest live
-- in course_waypoint. Grade-adjusted splits are computed on read from the
-- athlete's own sec/km-effort — nothing pace-related is persisted.

CREATE TABLE course (
    id                uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id           uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    objective_id      uuid          NOT NULL UNIQUE REFERENCES objective (id) ON DELETE CASCADE,
    name              varchar(150)  NOT NULL,
    distance_km       numeric(6, 2) NOT NULL CONSTRAINT course_distance_positive CHECK (distance_km > 0),
    elevation_gain_m  int           NOT NULL DEFAULT 0,
    elevation_loss_m  int           NOT NULL DEFAULT 0,
    -- downsampled cumulative profile: JSON [[distance_m, elevation_m], ...]
    profile_json      text          NOT NULL,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_course_user ON course (user_id);

CREATE TABLE course_waypoint (
    id           uuid          PRIMARY KEY DEFAULT uuidv7(),
    course_id    uuid          NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    name         varchar(120)  NOT NULL,
    kind         varchar(15)   NOT NULL
        CONSTRAINT course_waypoint_kind_check CHECK (kind IN ('AID_STATION', 'SUMMIT', 'POINT')),
    distance_km  numeric(6, 2) NOT NULL CONSTRAINT course_waypoint_distance_check CHECK (distance_km >= 0),
    elevation_m  int,
    note         varchar(300),
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_course_waypoint_course ON course_waypoint (course_id);
