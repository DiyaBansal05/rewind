-- Existing enrollment rows predate the admin-approval workflow and represent
-- students who are already genuinely part of a batch -- they must stay
-- APPROVED (i.e. stay visible/functional) and must NOT be reset to PENDING.
-- Only enrollments created from this point on default to PENDING (set in
-- application code, not here) and require an explicit admin decision.
alter table enrollment add column status varchar(20) not null default 'APPROVED';
alter table enrollment add column decided_at timestamptz;
alter table enrollment add column decided_by_admin_id uuid references admin(id);

update enrollment set decided_at = enrolled_at where status = 'APPROVED';

alter table enrollment alter column status drop default;

create index idx_enrollment_status on enrollment(status);
