# Institute Recording Portal

A web platform that automates recording access for a small educational institute. Courses ("batches") run for weeks to months, taught live over Zoom. When a student misses a class, the current manual process — instructor digs up the Zoom cloud recording, sets an access/expiry limit, sends it over chat — doesn't scale. This app removes that manual work end-to-end.

## What it does

- Students self-register into a batch by scanning a per-batch QR code — no manual data entry by the admin.
- Students request the recording for a specific missed class through a simple portal.
- The admin gets a one-tap approval queue instead of digging through Zoom manually.
- On approval, the backend resolves the correct Zoom cloud recording (matched by the batch's class time window, since all batches share one Zoom account) and issues a time-limited, revocable access link.
- Per-student recording-request history, so the admin can see who requests most often.
- An admin-only natural-language chatbot ("how many requests today?", "details of a student?") backed by Claude tool-use over a fixed set of read-only report queries — no free-form text-to-SQL.

## Architecture

```
React + TS + Vite SPA  (Vercel)
        │ HTTPS/JSON, JWT bearer
Spring Boot API  (Render free web service)
Controllers → Services → Repositories (JPA/Hibernate)
        │                 │                  │
Postgres           Zoom S2S OAuth API   Claude API (tool-use)
                    (cloud recordings)   admin chatbot
        │
NotificationService interface
 ├─ InAppNotificationServiceImpl
 └─ WhatsAppCloudNotificationServiceImpl  (Meta free test number)
```

**End-to-end flow:** student scans batch QR → self-registers → logs in via phone-number magic-link/OTP → requests a missed date's recording → admin's approval queue (one-tap approve/deny) → on approve, backend confirms the recording exists in Zoom, issues its own signed time-limited token, notifies the student → student opens the link → backend validates the token, fetches a *fresh* Zoom download URL server-side, streams the video through the backend (Zoom credentials never reach the client) → access is logged, token expires after 48–72h → admin dashboard/chatbot can query request history and per-student frequency.

## Core Data Model

- **Admin** — id, email, passwordHash
- **Batch** — id, name, courseName, startDate, endDate, zoomMeetingId (one shared Zoom account across all batches), classDaysOfWeek, classStartTime, classEndTime, status
- **Student** — id, name, phoneNumber (unique)
- **Enrollment** — studentId, batchId, enrolledAt (created via QR self-registration; unique per student+batch)
- **RecordingRequest** — id, studentId, batchId, classDate, status (PENDING/APPROVED/DENIED/REVOKED/EXPIRED), requestedAt, decidedAt, accessTokenHash, accessExpiresAt, zoomRecordingFileId
- **AccessEvent** — recordingRequestId, accessedAt, success (audit trail; feeds "who requests most" analytics)
- **ChatbotQueryLog** — adminId, userQuestion, toolsInvoked (JSON), finalAnswer, createdAt
- **NotificationLog** — recipientType, recipientId, channel, payloadSummary, sentAt, deliveryStatus

```java
public interface NotificationService {
    void notifyAdminRequestRaised(Admin admin, RecordingRequest request);       // admin, on every new request
    void notifyStudentRecordingApproved(Student student, RecordingRequest request, String accessUrl);
    void notifyStudentMagicLink(Student student, String magicLinkUrl);
}
```

## Key Design Decisions

**QR registration:** QR encodes a signed token (HMAC/JJWT over `{batchId, iat}`, backend secret) — not a raw batch ID — so a student can't tamper with the URL to enroll into an arbitrary batch. Batch creation requires class schedule (days of week + start/end time) as required fields, needed for recording resolution below.

**Recording resolution:** All batches share one Zoom meeting ID, so a single day can produce multiple recording files (different batches at different times). On approval, the backend lists Zoom recordings for the account on `classDate`, then matches the file whose `start_time` falls within `[batch.classStartTime - 15min, batch.classEndTime + 15min]`. Zero or multiple matches are surfaced to the admin to pick manually rather than auto-resolved.

**Recording access:** On approval, the backend confirms the recording exists via Zoom's S2S OAuth API, then issues its own signed, time-limited opaque token (JJWT, `sub=recordingRequestId`, `exp`=+48–72h; only the *hash* is stored). Redemption endpoint (`GET /r/{token}`) validates the token, fetches a fresh Zoom `download_url` at redeem-time, and proxy-streams the video through the Spring backend — Zoom's bearer token never reaches the client, and revocation is just a status flip. Same signed-URL pattern used industry-wide (S3 pre-signed URLs).

Zoom setup: **Internal App → Server-to-Server OAuth** app in the Zoom Marketplace. Classic scopes `recording:read:admin` / `recording:write:admin`.

**Auth:** Admin uses email+password → JWT (`ROLE_ADMIN`). Students use phone number → OTP/magic-link → JWT (`ROLE_STUDENT`), no password. Stateless Spring Security, one `JwtAuthenticationFilter`, `@PreAuthorize` role checks. `/r/{token}` is its own signed-token scheme, separate from student JWT.

**Chatbot (Claude tool-use, admin-only):** Fixed read-only tools: `get_request_count_for_date_range`, `get_top_requesters_for_batch`, `get_student_details`, `get_batch_summary`. Model: `claude-haiku-4-5`. Manual tool-use loop, validate every `tool_use.input` server-side, never expose a write/approve/delete tool to the model, gate behind `ROLE_ADMIN`, log every query to `ChatbotQueryLog`.

**Notifications:** `InAppNotificationServiceImpl` for student-facing messages until Phase 3. `WhatsAppCloudNotificationServiceImpl` built in **Phase 1** for `notifyAdminRequestRaised` only (single fixed recipient, works on Meta's free test-number tier with no business verification needed). Extended in **Phase 3** to cover `notifyStudentRecordingApproved` once it needs to scale to many student numbers.

**Stack:** Spring Boot 4.1, Java 17, Maven, Spring Web/Security/Data JPA/Validation, PostgreSQL, Flyway. Frontend: Vite + React + TypeScript + Tailwind, React Router. Monorepo: `backend/` + `frontend/`.

**Hosting (free tier):** Render (backend), Vercel (frontend), Neon (Postgres, scale-to-zero).

## Phased Roadmap

1. **Phase 0 — Scaffolding** ✅ monorepo skeleton, Spring Boot + local Postgres, Vite+React+TS+Tailwind, health-check round trip, CI.
2. **Phase 1 — MVP core (mocked recording links, real admin WhatsApp ping):** entities + Flyway migrations, admin login, QR-signed registration + self-enrollment, student magic-link login (OTP shown in-app), recording request → real WhatsApp message to admin → approval queue → fake signed link → placeholder video page, per-student request history.
3. **Phase 2 — Real Zoom integration:** S2S OAuth + token caching, real recording lookup with time-window matching, real signed-token redemption + proxy-streaming.
4. **Phase 3 — WhatsApp notifications to students:** extend `WhatsAppCloudNotificationServiceImpl` to cover approval notifications.
5. **Phase 4 — Claude chatbot:** fixed tool set, `ChatbotReportService`, admin chat UI, audit logging.
6. **Phase 5 — Polish & deploy:** mobile-first pass, empty/error states, rate-limiting, deploy, architecture diagram.

## Local Development

**Backend:**
```
cd backend
./mvnw spring-boot:run
```

**Frontend:**
```
cd frontend
npm run dev
```

Requires a local Postgres instance (see `docker-compose.yml` or a native install) matching the credentials in `backend/src/main/resources/application.yml`.
