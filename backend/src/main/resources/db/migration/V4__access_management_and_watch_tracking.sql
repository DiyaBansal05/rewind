-- Watch tracking: the recording link now opens a small player page that
-- reports playback progress, so the admin can see whether (and how much of)
-- an approved recording was actually watched.
--
-- watched_segments is a coverage bitmap over the video, one character per
-- WatchCoverage.SEGMENT_SECONDS slice ('1' = played at least once). Coverage
-- rather than "furthest position" so skipping straight to the end doesn't
-- count as having watched the whole class.
alter table recording_request add column duration_seconds integer;
alter table recording_request add column watched_segments text;
alter table recording_request add column last_position_seconds integer not null default 0;
alter table recording_request add column last_watched_at timestamptz;

-- Each AccessEvent is one opening of the player page (a "viewing session");
-- progress pings update the session they belong to, which gives the admin a
-- per-session timeline ("opened at 9:38 PM, played to 0:31:05").
alter table access_event add column max_position_seconds integer;
alter table access_event add column last_progress_at timestamptz;
alter table access_event add column user_agent varchar(255);

-- Analytics and filtered request lists scan by time.
create index idx_recording_request_requested_at on recording_request(requested_at);
create index idx_access_event_request on access_event(recording_request_id);
