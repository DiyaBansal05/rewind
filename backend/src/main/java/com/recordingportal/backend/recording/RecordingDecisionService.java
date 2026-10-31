package com.recordingportal.backend.recording;

import com.recordingportal.backend.admin.Admin;
import com.recordingportal.backend.admin.AdminRepository;
import com.recordingportal.backend.notification.NotificationService;
import com.recordingportal.backend.zoom.ZoomLookupNotAllowedException;
import com.recordingportal.backend.zoom.ZoomRecordingLookupService.RecordingFileCandidate;
import com.recordingportal.backend.zoom.ZoomRecordingMatcher;
import com.recordingportal.backend.zoom.ZoomRecordingMatcher.MatchResult;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every admin decision on a recording request -- approve, deny, extend,
 * revoke -- lives here so the single-request endpoints and the bulk
 * endpoints behave identically.
 */
@Service
public class RecordingDecisionService {

    private static final Logger log = LoggerFactory.getLogger(RecordingDecisionService.class);

    /** Longest single extension the admin can grant. */
    public static final int MAX_EXTEND_DAYS = 30;

    private final RecordingRequestRepository recordingRequestRepository;
    private final AdminRepository adminRepository;
    private final AccessTokenService accessTokenService;
    private final NotificationService notificationService;
    private final ZoomRecordingMatcher zoomRecordingMatcher;
    private final String backendBaseUrl;

    public RecordingDecisionService(
            RecordingRequestRepository recordingRequestRepository,
            AdminRepository adminRepository,
            AccessTokenService accessTokenService,
            NotificationService notificationService,
            ZoomRecordingMatcher zoomRecordingMatcher,
            @Value("${app.backend-base-url}") String backendBaseUrl) {
        this.recordingRequestRepository = recordingRequestRepository;
        this.adminRepository = adminRepository;
        this.accessTokenService = accessTokenService;
        this.notificationService = notificationService;
        this.zoomRecordingMatcher = zoomRecordingMatcher;
        this.backendBaseUrl = backendBaseUrl;
    }

    public enum Outcome {
        APPROVED, DENIED, EXTENDED, REVOKED,
        NOT_FOUND, ALREADY_DECIDED, NOT_ACTIVE, INVALID_CANDIDATE,
        NO_MATCH, MULTIPLE_CANDIDATES, LOOKUP_NOT_ALLOWED, ERROR
    }

    public record Result(
            UUID id, Outcome outcome, String message,
            List<RecordingFileCandidate> candidates, RecordingRequest request) {
        public boolean ok() {
            return outcome == Outcome.APPROVED || outcome == Outcome.DENIED
                    || outcome == Outcome.EXTENDED || outcome == Outcome.REVOKED;
        }

        static Result of(UUID id, Outcome outcome, String message) {
            return new Result(id, outcome, message, List.of(), null);
        }
    }

    /**
     * Zoom match results keyed by (batch, date), so a bulk approval of many
     * requests for the same class only asks Zoom once.
     */
    public static final class MatchCache {
        private final Map<String, MatchResult> results = new HashMap<>();
    }

    @Transactional
    public Result approve(UUID id, String zoomRecordingFileId, UUID adminId, MatchCache cache) {
        RecordingRequest request = recordingRequestRepository.findWithDetailsById(id).orElse(null);
        if (request == null) {
            return Result.of(id, Outcome.NOT_FOUND, "Request not found.");
        }
        if (request.getStatus() != RecordingRequestStatus.PENDING) {
            return Result.of(id, Outcome.ALREADY_DECIDED, "This request was already approved or denied.");
        }

        MatchResult matchResult;
        try {
            matchResult = match(request, cache);
        } catch (ZoomLookupNotAllowedException e) {
            return Result.of(id, Outcome.LOOKUP_NOT_ALLOWED, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Zoom lookup failed for request {}", id, e);
            return Result.of(id, Outcome.ERROR, "Couldn't reach Zoom. Try again in a moment.");
        }

        RecordingFileCandidate chosen;
        if (zoomRecordingFileId != null) {
            chosen = matchResult.candidates().stream()
                    .filter(c -> zoomRecordingFileId.equals(c.recordingFileId()))
                    .findFirst()
                    .orElse(null);
            if (chosen == null) {
                return Result.of(id, Outcome.INVALID_CANDIDATE, "That recording is no longer a match for this class.");
            }
        } else if (matchResult.candidates().isEmpty()) {
            return Result.of(id, Outcome.NO_MATCH, "No matching Zoom recording found for this class date/time. "
                    + "The class may not have been recorded, or processing isn't complete yet.");
        } else if (!matchResult.isUnambiguous()) {
            return new Result(id, Outcome.MULTIPLE_CANDIDATES,
                    "More than one recording matches this class time. Pick the right one.",
                    matchResult.candidates(), request);
        } else {
            chosen = matchResult.candidates().get(0);
        }

        Admin admin = adminRepository.findById(adminId).orElseThrow();
        Instant now = Instant.now();
        AccessTokenService.IssuedToken issued = accessTokenService.issue(request.getId(), now);

        request.setStatus(RecordingRequestStatus.APPROVED);
        request.setDecidedAt(now);
        request.setDecidedByAdmin(admin);
        request.setAccessTokenHash(issued.hash());
        request.setAccessExpiresAt(issued.expiresAt());
        request.setZoomRecordingFileId(chosen.recordingFileId());
        request.setZoomMeetingUuid(chosen.meetingUuid());
        recordingRequestRepository.save(request);

        notificationService.notifyStudentRecordingApproved(request, accessUrl(issued.rawToken()));
        return new Result(id, Outcome.APPROVED, null, List.of(), request);
    }

    @Transactional
    public Result deny(UUID id, UUID adminId) {
        RecordingRequest request = recordingRequestRepository.findWithDetailsById(id).orElse(null);
        if (request == null) {
            return Result.of(id, Outcome.NOT_FOUND, "Request not found.");
        }
        if (request.getStatus() != RecordingRequestStatus.PENDING) {
            return Result.of(id, Outcome.ALREADY_DECIDED, "This request was already approved or denied.");
        }
        Admin admin = adminRepository.findById(adminId).orElseThrow();
        request.setStatus(RecordingRequestStatus.DENIED);
        request.setDecidedAt(Instant.now());
        request.setDecidedByAdmin(admin);
        recordingRequestRepository.save(request);
        return new Result(id, Outcome.DENIED, null, List.of(), request);
    }

    /**
     * Pushes the expiry forward by {@code days}. Works on an active link
     * (adds to the remaining time) and on one that has already expired
     * (re-opens it, counted from now). The student's link stays the same.
     */
    @Transactional
    public Result extend(UUID id, int days) {
        if (days < 1 || days > MAX_EXTEND_DAYS) {
            return Result.of(id, Outcome.ERROR, "Extend by 1 to " + MAX_EXTEND_DAYS + " days.");
        }
        RecordingRequest request = recordingRequestRepository.findWithDetailsById(id).orElse(null);
        if (request == null) {
            return Result.of(id, Outcome.NOT_FOUND, "Request not found.");
        }
        if (request.getStatus() != RecordingRequestStatus.APPROVED && request.getStatus() != RecordingRequestStatus.EXPIRED) {
            return Result.of(id, Outcome.NOT_ACTIVE, "Only approved or expired recordings can be extended.");
        }
        Instant now = Instant.now();
        Instant base = request.getAccessExpiresAt() != null && request.getAccessExpiresAt().isAfter(now)
                ? request.getAccessExpiresAt()
                : now;
        request.setAccessExpiresAt(base.plus(Duration.ofDays(days)));
        request.setStatus(RecordingRequestStatus.APPROVED);
        recordingRequestRepository.save(request);

        String url = accessTokenService.rebuildToken(request).map(this::accessUrl).orElse(null);
        notificationService.notifyStudentAccessExtended(request, url);
        return new Result(id, Outcome.EXTENDED, null, List.of(), request);
    }

    /** Ends access immediately; the link stops working on its next request. */
    @Transactional
    public Result revoke(UUID id) {
        RecordingRequest request = recordingRequestRepository.findWithDetailsById(id).orElse(null);
        if (request == null) {
            return Result.of(id, Outcome.NOT_FOUND, "Request not found.");
        }
        if (request.getStatus() != RecordingRequestStatus.APPROVED) {
            return Result.of(id, Outcome.NOT_ACTIVE, "Only an active recording link can be revoked.");
        }
        request.setStatus(RecordingRequestStatus.REVOKED);
        recordingRequestRepository.save(request);
        return new Result(id, Outcome.REVOKED, null, List.of(), request);
    }

    public String accessUrl(String rawToken) {
        return backendBaseUrl + "/r/" + rawToken;
    }

    private MatchResult match(RecordingRequest request, MatchCache cache) {
        if (cache == null) {
            return zoomRecordingMatcher.match(request.getBatch(), request.getClassDate());
        }
        String key = request.getBatch().getId() + "|" + request.getClassDate();
        MatchResult cached = cache.results.get(key);
        if (cached != null) return cached;
        MatchResult result = zoomRecordingMatcher.match(request.getBatch(), request.getClassDate());
        cache.results.put(key, result);
        return result;
    }
}
