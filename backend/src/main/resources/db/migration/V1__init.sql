create table admin (
    id uuid primary key,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    created_at timestamptz not null
);

create table batch (
    id uuid primary key,
    name varchar(255) not null,
    course_name varchar(255) not null,
    start_date date not null,
    end_date date not null,
    zoom_meeting_id varchar(255),
    class_start_time time not null,
    class_end_time time not null,
    status varchar(50) not null,
    created_at timestamptz not null
);

create table batch_class_days (
    batch_id uuid not null references batch(id) on delete cascade,
    day_of_week varchar(20) not null
);

create table student (
    id uuid primary key,
    name varchar(255) not null,
    phone_number varchar(50) not null unique,
    created_at timestamptz not null
);

create table enrollment (
    id uuid primary key,
    student_id uuid not null references student(id) on delete cascade,
    batch_id uuid not null references batch(id) on delete cascade,
    enrolled_at timestamptz not null,
    unique (student_id, batch_id)
);

create table recording_request (
    id uuid primary key,
    student_id uuid not null references student(id) on delete cascade,
    batch_id uuid not null references batch(id) on delete cascade,
    class_date date not null,
    status varchar(50) not null,
    requested_at timestamptz not null,
    decided_at timestamptz,
    decided_by_admin_id uuid references admin(id),
    access_token_hash varchar(255),
    access_expires_at timestamptz,
    zoom_recording_file_id varchar(255)
);

create table access_event (
    id uuid primary key,
    recording_request_id uuid not null references recording_request(id) on delete cascade,
    accessed_at timestamptz not null,
    success boolean not null
);

create table notification_log (
    id uuid primary key,
    recipient_type varchar(50) not null,
    recipient_id uuid not null,
    channel varchar(50) not null,
    payload_summary varchar(1000) not null,
    sent_at timestamptz not null,
    delivery_status varchar(50) not null
);

create index idx_recording_request_status on recording_request(status);
create index idx_recording_request_student on recording_request(student_id);
create index idx_enrollment_student on enrollment(student_id);
