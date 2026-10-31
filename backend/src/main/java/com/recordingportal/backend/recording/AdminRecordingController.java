package com.recordingportal.backend.recording;

import com.recordingportal.backend.recording.RecordingDecisionService.MatchCache;
import com.recordingportal.backend.recording.RecordingDecisionService.Outcome;
import com.recordingportal.backend.recording.RecordingDecisionService.Result;
import com.recordingportal.backend.zoom.ZoomRecordingLookupService.RecordingFileCandidate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/recording-requests")
public class AdminRecordingController {

    /** Lists are for a single institute's admin; this keeps "All" fast without paging. */
    private static final int MAX_LIST_SIZE = 500;
    /** Bulk actions are a convenience for a busy morning queue, not a batch job. */
    private static final int MAX_BULK_SIZE = 100;

    private final RecordingRequestRepository recordingRequestRepository;
    private final AccessEventRepository accessEventRepository;
    private final RecordingDecisionService decisionService;
    private final AccessTokenService accessTokenService;
    private final String backendBaseUrl;

    public AdminRecordingController(
            RecordingRequestRepository recordingRequestRepository,
            AccessEventRepository accessEventRepository,
            RecordingDecisionService decisionService,
            AccessTokenService accessTokenService,
            @Value("${app.backend-base-url}") String backendBaseUrl) {
        this.recordingRequestRepository = recordingRequestRepository;
        this.accessEventRepository = accessEventRepository;
        this.decisionService = decisionService;
        this.accessTokenService = accessTokenService;
        this.backendBaseUrl = backendBaseUrl;
    }

    public record QueueItem(
            UUID id, String studentName, String studentPhone, UUID batchId, String batchName,
            LocalDate classDate, RecordingRequestStatus status, Instant requestedAt,
            Instant decidedAt, Instant accessExpiresAt, int watchPercent, long openCount, Instant lastWatchedAt) {
        static QueueItem from(RecordingRequest r, long openCount) {
            return new QueueItem(
                    r.getId(), r.getStudent().getName(), r.getStudent().getPhoneNumber(),
                    r.getBatch().getId(), r.getBatch().getName(), r.getClassDate(),
                    r.getStatus(), r.getRequestedAt(), r.getDecidedAt(), r.getAccessExpiresAt(),
                    WatchCoverage.percent(r.getWatchedSegments()), openCount, r.getLastWatchedAt());
        }
    }

    public record ViewingSession(Instant openedAt, boolean success, Integer maxPositionSeconds, Instant lastProgressAt, String device) {
    }

    public record RequestDetail(
            QueueItem summary, Integer durationSeconds, int watchedSeconds, int lastPositionSeconds,
            Instant firstOpenedAt, List<ViewingSession> sessions) {
    }

    public record ApproveRequest(String zoomRecordingFileId) {
    }

    public record BulkRequest(@NotEmpty @Size(max = MAX_BULK_SIZE) List<UUID> ids) {
    }

    public record ExtendRequest(int days) {
    }

    public record CandidateView(String recordingFileId, String meetingTopic, Instant startTime, String previewUrl) {
        static CandidateView from(RecordingFileCandidate c, AccessTokenService accessTokenService, String backendBaseUrl) {
            String previewToken = accessTokenService.issuePreview(c.meetingUuid(), c.recordingFileId()).rawToken();
            String previewUrl = backendBaseUrl + "/r/preview/" + previewToken;
            return new CandidateView(c.recordingFileId(), c.meetingTopic(), c.startTime(), previewUrl);
        }
    }

    public record BulkItemResult(UUID id, Outcome outcome, String message, List<CandidateView> candidates) {
    }

    /**
     * Filterable request list. With no parameters it returns the pending
     * queue, oldest first -- the original behavior, which the nav badge
     * still relies on. {@code status=ALL} returns everything, newest first.
     */
    @GetMapping
    public List<QueueItem> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RecordingRequestStatus statusFilter = parseStatus(status);
        Stream<RecordingRequest> rows = statusFilter == RecordingRequestStatus.PENDING
                ? recordingRequestRepository.findByStatusOrderByRequestedAtAsc(RecordingRequestStatus.PENDING).stream()
                : recordingRequestRepository.findAllWithDetails().stream()
                        .filter(r -> statusFilter == null || r.getStatus() == statusFilter);

        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String digits = needle.replaceAll("\\D", "");
        List<RecordingRequest> filtered = rows
                .filter(r -> batchId == null || r.getBatch().getId().equals(batchId))
                .filter(r -> from == null || !r.getClassDate().isBefore(from))
                .filter(r -> to == null || !r.getClassDate().isAfter(to))
                .filter(r -> needle.isEmpty() || matches(r, needle, digits))
                .limit(MAX_LIST_SIZE)
                .toList();
        return toQueueItems(filtered);
    }

    @GetMapping("/counts")
    public Map<String, Long> counts() {
        Map<RecordingRequestStatus, Long> byStatus = new EnumMap<>(RecordingRequestStatus.class);
        for (RecordingRequestStatus s : RecordingRequestStatus.values()) byStatus.put(s, 0L);
        for (Object[] row : recordingRequestRepository.countGroupedByStatus()) {
            byStatus.put((RecordingRequestStatus) row[0], (Long) row[1]);
        }
        Map<String, Long> result = new LinkedHashMap<>();
        byStatus.forEach((k, v) -> result.put(k.name(), v));
        result.put("ALL", byStatus.values().stream().mapToLong(Long::longValue).sum());
        return result;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RequestDetail> detail(@PathVariable UUID id) {
        RecordingRequest r = recordingRequestRepository.findWithDetailsById(id).orElse(null);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        List<AccessEvent> events = accessEventRepository.findByRecordingRequestIdOrderByAccessedAtDesc(id);
        long opens = events.stream().filter(AccessEvent::isSuccess).count();
        Instant firstOpened = events.stream().filter(AccessEvent::isSuccess)
                .map(AccessEvent::getAccessedAt).min(Comparator.naturalOrder()).orElse(null);
        List<ViewingSession> sessions = events.stream()
                .map(e -> new ViewingSession(e.getAccessedAt(), e.isSuccess(), e.getMaxPositionSeconds(),
                        e.getLastProgressAt(), DeviceLabel.from(e.getUserAgent())))
                .toList();
        return ResponseEntity.ok(new RequestDetail(
                QueueItem.from(r, opens), r.getDurationSeconds(),
                WatchCoverage.watchedSeconds(r.getWatchedSegments(), r.getDurationSeconds()),
                r.getLastPositionSeconds(), firstOpened, sessions));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveRequest body,
            Principal principal) {
        String fileId = body != null ? body.zoomRecordingFileId() : null;
        return single(decisionService.approve(id, fileId, adminId(principal), null));
    }

    @PostMapping("/{id}/deny")
    public ResponseEntity<?> deny(@PathVariable UUID id, Principal principal) {
        return single(decisionService.deny(id, adminId(principal)));
    }

    @PostMapping("/{id}/extend")
    public ResponseEntity<?> extend(@PathVariable UUID id, @RequestBody ExtendRequest body) {
        return single(decisionService.extend(id, body.days()));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<?> revoke(@PathVariable UUID id) {
        return single(decisionService.revoke(id));
    }

    /**
     * Approves each request independently and reports per-row outcomes.
     * Rows that need a human choice (several matching recordings) come back
     * as MULTIPLE_CANDIDATES with their candidate list, same as the
     * single-request endpoint, so the UI can show the picker for just those.
     */
    @PostMapping("/bulk-approve")
    public List<BulkItemResult> bulkApprove(@Valid @RequestBody BulkRequest body, Principal principal) {
        UUID adminId = adminId(principal);
        MatchCache cache = new MatchCache();
        return new LinkedHashSet<>(body.ids()).stream()
                .map(id -> decisionService.approve(id, null, adminId, cache))
                .map(this::toBulkResult)
                .toList();
    }

    @PostMapping("/bulk-deny")
    public List<BulkItemResult> bulkDeny(@Valid @RequestBody BulkRequest body, Principal principal) {
        UUID adminId = adminId(principal);
        return new LinkedHashSet<>(body.ids()).stream()
                .map(id -> decisionService.deny(id, adminId))
                .map(this::toBulkResult)
                .toList();
    }

    private ResponseEntity<?> single(Result result) {
        if (result.ok()) {
            long opens = countOpens(List.of(result.id())).getOrDefault(result.id(), 0L);
            return ResponseEntity.ok(QueueItem.from(result.request(), opens));
        }
        if (result.outcome() == Outcome.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        HttpStatus status = switch (result.outcome()) {
            case INVALID_CANDIDATE, ERROR -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
        Map<String, Object> body = new HashMap<>();
        body.put("reason", result.outcome().name());
        if (result.message() != null) body.put("message", result.message());
        if (result.outcome() == Outcome.MULTIPLE_CANDIDATES) body.put("candidates", candidateViews(result));
        return ResponseEntity.status(status).body(body);
    }

    private BulkItemResult toBulkResult(Result result) {
        List<CandidateView> candidates = result.outcome() == Outcome.MULTIPLE_CANDIDATES ? candidateViews(result) : List.of();
        return new BulkItemResult(result.id(), result.outcome(), result.message(), candidates);
    }

    private List<CandidateView> candidateViews(Result result) {
        return result.candidates().stream()
                .map(c -> CandidateView.from(c, accessTokenService, backendBaseUrl))
                .toList();
    }

    private List<QueueItem> toQueueItems(List<RecordingRequest> requests) {
        Map<UUID, Long> opens = countOpens(requests.stream().map(RecordingRequest::getId).toList());
        return requests.stream().map(r -> QueueItem.from(r, opens.getOrDefault(r.getId(), 0L))).toList();
    }

    private Map<UUID, Long> countOpens(List<UUID> ids) {
        Map<UUID, Long> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        for (Object[] row : accessEventRepository.countSuccessfulOpens(ids)) {
            result.put((UUID) row[0], (Long) row[1]);
        }
        return result;
    }

    private static boolean matches(RecordingRequest r, String needle, String digits) {
        if (r.getStudent().getName().toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (r.getBatch().getName().toLowerCase(Locale.ROOT).contains(needle)) return true;
        return digits.length() >= 3 && r.getStudent().getPhoneNumber().replaceAll("\\D", "").contains(digits);
    }

    /** null = all statuses. Missing/blank defaults to PENDING (the original queue). */
    private static RecordingRequestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return RecordingRequestStatus.PENDING;
        if ("ALL".equalsIgnoreCase(status)) return null;
        try {
            return RecordingRequestStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RecordingRequestStatus.PENDING;
        }
    }

    private static UUID adminId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
