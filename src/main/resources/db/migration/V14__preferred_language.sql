-- The language the interface AND generated coaching content (plan names,
-- week focus, session instructions) should use for this athlete.
ALTER TABLE users
    ADD COLUMN preferred_language VARCHAR(2) NOT NULL DEFAULT 'fr'
    CONSTRAINT users_preferred_language_check CHECK (preferred_language IN ('fr', 'en'));
