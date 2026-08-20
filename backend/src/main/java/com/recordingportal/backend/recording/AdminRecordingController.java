package com.recordingportal.backend.recording;

import com.recordingportal.backend.admin.Admin;
import com.recordingportal.backend.admin.AdminRepository;
import com.recordingportal.backend.notification.NotificationService;
import com.recordingportal.backend.zoom.ZoomLookupNotAllowedException;
import com.recordingportal.backend.zoom.ZoomRecordingLookupService.RecordingFileCandidate;
import com.recordingportal.backend.zoom.ZoomRecordingMatcher;
import com.recordingportal.backend.zoom.ZoomRecordingMatcher.MatchResult;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/recording-requests")
public class AdminRecordingController {

    private final RecordingRequestRepository recordingRequestRepository;
    private final AdminRepository adminRepository;
    private final AccessTokenService accessTokenService;
    private final NotificationService notificationService;
    private final ZoomRecordingMatcher zoomRecordingMatcher;

    public AdminRecordingController(
            RecordingRequestRepository recordingRequestRepository,
            AdminRepository adminRepository,
            AccessTokenService accessTokenService,
            NotificationService notificationService,
            ZoomRecordingMatcher zoomRecordingMatcher) {
        this.recordingRequestRepository = recordingRequestRepository;
        this.adminRepository = adminRepository;
        this.accessTokenService = accessTokenService;
        this.notificationService = notificationService;
        this.zoomRecordingMatcher = zoomRecordingMatcher;
    }

    public record QueueItem(
            UUID id, String studentName, String studentPhone, UUID batchId, String batchName,
            LocalDate classDate, RecordingRequestStatus status, Instant requestedAt) {
        static QueueItem from(RecordingRequest r) {
            return new QueueItem(
                    r.getId(), r.getStudent().getName(), r.getStudent().getPhoneNumber(),
                    r.getBatch().getId(), r.getBatch().getName(), r.getClassDate(),
                    r.getStatus(), r.getRequestedAt());
        }
    }

    public record ApproveRequest(String zoomRecordingFileId) {
    }

    public record CandidateView(String recordingFileId, String meetingTopic, Instant startTime) {
        static CandidateView from(RecordingFileCandidate c) {
            return new CandidateView(c.recordingFileId(), c.meetingTopic(), c.startTime());
        }
    }

    @GetMapping
    public List<QueueItem> pendingQueue() {
        return recordingRequestRepository.findByStatusOrderByRequestedAtAsc(RecordingRequestStatus.PENDING).stream()
                .map(QueueItem::from)
                .toList();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveRequest body,
            Principal principal) {
        RecordingRequest request = recordingRequestRepository.findWithDetailsById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.getStatus() != RecordingRequestStatus.PENDING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("reason", "ALREADY_DECIDED"));
        }

        MatchResult matchResult;
        try {
            matchResult = zoomRecordingMatcher.match(request.getBatch(), request.getClassDate());
        } catch (ZoomLookupNotAllowedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("reason", "LOOKUP_NOT_ALLOWED", "message", e.getMessage()));
        }

        String requestedFileId = body != null ? body.zoomRecordingFileId() : null;
        RecordingFileCandidate chosen;
        if (requestedFileId != null) {
            chosen = matchResult.candidates().stream()
                    .filter(c -> requestedFileId.equals(c.recordingFileId()))
                    .findFirst()
                    .orElse(null);
            if (chosen == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("reason", "INVALID_CANDIDATE"));
            }
        } else if (matchResult.candidates().isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "reason", "NO_MATCH",
                    "message", "No matching Zoom recording found for this class date/time. "
                            + "The class may not have been recorded, or processing isn't complete yet."));
        } else if (!matchResult.isUnambiguous()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "reason", "MULTIPLE_CANDIDATES",
                    "candidates", matchResult.candidates().stream().map(CandidateView::from).toList()));
        } else {
            chosen = matchResult.candidates().get(0);
        }

        Admin admin = adminRepository.findById(UUID.fromString(principal.getName())).orElseThrow();
        AccessTokenService.IssuedToken issued = accessTokenService.issue(request.getId());

        request.setStatus(RecordingRequestStatus.APPROVED);
        request.setDecidedAt(Instant.now());
        request.setDecidedByAdmin(admin);
        request.setAccessTokenHash(issued.hash());
        request.setAccessExpiresAt(issued.expiresAt());
        request.setZoomRecordingFileId(chosen.recordingFileId());
        request.setZoomMeetingUuid(chosen.meetingUuid());
        recordingRequestRepository.save(request);

        String accessUrl = "/r/" + issued.rawToken();
        notificationService.notifyStudentRecordingApproved(request, accessUrl);

        return ResponseEntity.ok(QueueItem.from(request));
    }

    @PostMapping("/{id}/deny")
    public ResponseEntity<?> deny(@PathVariable UUID id, Principal principal) {
        RecordingRequest request = recordingRequestRepository.findWithDetailsById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.getStatus() != RecordingRequestStatus.PENDING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("reason", "ALREADY_DECIDED"));
        }

        Admin admin = adminRepository.findById(UUID.fromString(principal.getName())).orElseThrow();
        request.setStatus(RecordingRequestStatus.DENIED);
        request.setDecidedAt(Instant.now());
        request.setDecidedByAdmin(admin);
        recordingRequestRepository.save(request);

        return ResponseEntity.ok(QueueItem.from(request));
    }
}
