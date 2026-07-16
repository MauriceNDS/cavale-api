-- Shoes gain a colour (the physical pair's colour, shown as an accent) and a
-- default flag — the pair pre-selected when validating a run. At most one
-- default per athlete is enforced in the service (it clears the others).
alter table shoe
    add column color      varchar(20),
    add column is_default boolean not null default false;
