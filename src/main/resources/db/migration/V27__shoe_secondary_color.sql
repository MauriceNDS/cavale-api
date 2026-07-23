-- A pair can have a two-tone colourway; the second colour helps the athlete
-- recognise the pair at a glance.
alter table shoe
    add column color_secondary varchar(20);

comment on column shoe.color_secondary is 'Optional second colour of the pair, as a hex string.';
