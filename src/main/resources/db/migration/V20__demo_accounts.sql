-- Ephemeral demo accounts for the public portfolio: a recruiter spins up a
-- private, fully-seeded sandbox that auto-deletes after a TTL. is_demo tags them.
alter table users
    add column is_demo boolean not null default false;

-- Make every user-owned FK cascade so reaping a demo account is a single atomic
-- `delete from users` that CANNOT leak orphaned rows (strava_connection, shoe
-- and course already cascade). Good hygiene for real account deletion too.
alter table training_plan
    drop constraint training_plan_user_id_fkey,
    add constraint training_plan_user_id_fkey
        foreign key (user_id) references users (id) on delete cascade;

alter table planned_session
    drop constraint planned_session_user_id_fkey,
    add constraint planned_session_user_id_fkey
        foreign key (user_id) references users (id) on delete cascade;

alter table activity
    drop constraint activity_user_id_fkey,
    add constraint activity_user_id_fkey
        foreign key (user_id) references users (id) on delete cascade;

alter table objective
    drop constraint objective_user_id_fkey,
    add constraint objective_user_id_fkey
        foreign key (user_id) references users (id) on delete cascade;

alter table activity_best_effort
    drop constraint activity_best_effort_user_id_fkey,
    add constraint activity_best_effort_user_id_fkey
        foreign key (user_id) references users (id) on delete cascade;

alter table exercise
    drop constraint exercise_user_id_fkey,
    add constraint exercise_user_id_fkey
        foreign key (user_id) references users (id) on delete cascade;

alter table gym_template
    drop constraint gym_template_user_id_fkey,
    add constraint gym_template_user_id_fkey
        foreign key (user_id) references users (id) on delete cascade;

alter table workout_log
    drop constraint workout_log_user_id_fkey,
    add constraint workout_log_user_id_fkey
        foreign key (user_id) references users (id) on delete cascade;
