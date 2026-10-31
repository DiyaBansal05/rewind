package com.recordingportal.backend.recording;

import com.recordingportal.backend.batch.Batch;
import com.recordingportal.backend.batch.BatchRepository;
import com.recordingportal.backend.enrollment.EnrollmentRepository;
import com.recordingportal.backend.enrollment.EnrollmentStatus;
import com.recordingportal.backend.notification.NotificationService;
import com.recordingportal.backend.student.Student;
import com.recordingportal.backend.student.StudentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/recording-requests")
public class StudentRecordingController {

    private final RecordingRequestRepository recordingRequestRepository;
    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;
    private final AccessTokenService accessTokenService;
    private final RecordingDecisionService decisionService;

    public StudentRecordingController(
            RecordingRequestRepository recordingRequestRepository,
            StudentRepository studentRepository,
            BatchRepository batchRepository,
            EnrollmentRepository enrollmentRepository,
            NotificationService notificationService,
            AccessTokenService accessTokenService,
            RecordingDecisionService decisionService) {
        this.recordingRequestRepository = recordingRequestRepository;
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.notificationService = notificationService;
        this.accessTokenService = accessTokenService;
        this.decisionService = decisionService;
    }

    public record CreateRequest(@NotNull UUID batchId, @NotNull LocalDate classDate) {
    }

    /**
     * watchUrl is only set while the link works, so the dashboard can offer a
     * "Watch" button without the student digging through notifications.
     * Links approved before tokens became rebuildable have no watchUrl; the
     * one in the original notification still works for those.
     */
    public record RecordingRequestResponse(
            UUID id, UUID batchId, String batchName, LocalDate classDate,
            RecordingRequestStatus status, Instant requestedAt, Instant decidedAt,
            Instant accessExpiresAt, String watchUrl, int watchPercent,
            int lastPositionSeconds, Integer durationSeconds) {
    }

    private RecordingRequestResponse toResponse(RecordingRequest r) {
        boolean live = r.getStatus() == RecordingRequestStatus.APPROVED
                && r.getAccessExpiresAt() != null && r.getAccessExpiresAt().isAfter(Instant.now());
        String watchUrl = live ? accessTokenService.rebuildToken(r).map(decisionService::accessUrl).orElse(null) : null;
        return new RecordingRequestResponse(
                r.getId(), r.getBatch().getId(), r.getBatch().getName(), r.getClassDate(),
                live || r.getStatus() != RecordingRequestStatus.APPROVED ? r.getStatus() : RecordingRequestStatus.EXPIRED,
                r.getRequestedAt(), r.getDecidedAt(), r.getAccessExpiresAt(), watchUrl,
                WatchCoverage.percent(r.getWatchedSegments()), r.getLastPositionSeconds(), r.getDurationSeconds());
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateRequest request, java.security.Principal principal) {
        UUID studentId = UUID.fromString(principal.getName());
        Student student = studentRepository.findById(studentId).orElseThrow();

        if (!enrollmentRepository.existsByStudentIdAndBatchIdAndStatus(studentId, request.batchId(), EnrollmentStatus.APPROVED)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You're not enrolled in this batch");
        }
        Batch batch = batchRepository.findById(request.batchId()).orElseThrow();

        RecordingRequest recordingRequest = new RecordingRequest();
        recordingRequest.setStudent(student);
        recordingRequest.setBatch(batch);
        recordingRequest.setClassDate(request.classDate());
        recordingRequestRepository.save(recordingRequest);

        notificationService.notifyAdminRequestRaised(recordingRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(recordingRequest));
    }

    @GetMapping
    public List<RecordingRequestResponse> history(java.security.Principal principal) {
        UUID studentId = UUID.fromString(principal.getName());
        return recordingRequestRepository.findByStudentIdOrderByRequestedAtDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }
}
