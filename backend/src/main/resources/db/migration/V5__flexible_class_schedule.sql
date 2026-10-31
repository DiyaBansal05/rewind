-- Batches no longer share one start/end time across all class days: each
-- weekday can have its own time (e.g. Wed 11:00, Sat/Sun 22:00), and the
-- admin can change, cancel or add a class on a single date. Recording
-- matching uses whichever applies to the requested date.

create table batch_weekly_slot (
    batch_id uuid not null references batch(id) on delete cascade,
    day_of_week varchar(20) not null,
    start_time time not null,
    end_time time not null,
    unique (batch_id, day_of_week)
);

-- Carry every existing batch over unchanged: same days, same times.
insert into batch_weekly_slot (batch_id, day_of_week, start_time, end_time)
select d.batch_id, d.day_of_week, b.class_start_time, b.class_end_time
from batch_class_days d
join batch b on b.id = d.batch_id;

drop table batch_class_days;
alter table batch drop column class_start_time;
alter table batch drop column class_end_time;

-- One row per (batch, date) the admin changed. cancelled = no class that
-- day; otherwise start/end replace the usual time (or add a class on a day
-- that normally has none).
create table batch_class_change (
    id uuid primary key,
    batch_id uuid not null references batch(id) on delete cascade,
    class_date date not null,
    cancelled boolean not null,
    start_time time,
    end_time time,
    note varchar(255),
    created_at timestamptz not null,
    unique (batch_id, class_date),
    check (cancelled or (start_time is not null and end_time is not null))
);
