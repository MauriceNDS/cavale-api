-- Athlete availability: the one signal plan generation must never ignore.
ALTER TABLE users ADD COLUMN athlete_status varchar(10) NOT NULL DEFAULT 'AVAILABLE';
ALTER TABLE users ADD COLUMN status_note text;
ALTER TABLE users ADD COLUMN status_since date;

-- Pain/niggle reported at validation — early-warning trail for injuries.
ALTER TABLE activity ADD COLUMN pain_flag boolean NOT NULL DEFAULT false;
