-- Free-text athlete comment on a planned session (the description/detail
-- carries the coach's structure; the comment carries everything else).

alter table planned_session
    add column comment text;
