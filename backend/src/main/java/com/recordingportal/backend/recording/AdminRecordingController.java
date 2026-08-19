package com.recordingportal.backend.recording;

import com.recordingportal.backend.admin.Admin;
import com.recordingportal.backend.admin.AdminRepository;
import com.recordingportal.backend.notification.NotificationService;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/recording-requests")
public class AdminRecordingController {

    private final RecordingRequestRepository recordingRequestRepository;
    private final AdminRepository adminRepository;
    private final AccessTokenService accessTokenService;
    private final NotificationService notificationService;

    public AdminRecordingController(
            RecordingRequestRepository recordingRequestRepository,
            AdminRepository adminRepository,
            AccessTokenService accessTokenService,
            NotificationService notificationService) {
        this.recordingRequestRepository = recordingRequestRepository;
        this.adminRepository = adminRepository;
        this.accessTokenService = accessTokenService;
        this.notificationService = notificationService;
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

    @GetMapping
    public List<QueueItem> pendingQueue() {
        return recordingRequestRepository.findByStatusOrderByRequestedAtAsc(RecordingRequestStatus.PENDING).stream()
                .map(QueueItem::from)
                .toList();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable UUID id, Principal principal) {
        RecordingRequest request = recordingRequestRepository.findWithDetailsById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.getStatus() != RecordingRequestStatus.PENDING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Request already decided");
        }

        Admin admin = adminRepository.findById(UUID.fromString(principal.getName())).orElseThrow();
        AccessTokenService.IssuedToken issued = accessTokenService.issue(request.getId());

        request.setStatus(RecordingRequestStatus.APPROVED);
        request.setDecidedAt(Instant.now());
        request.setDecidedByAdmin(admin);
        request.setAccessTokenHash(issued.hash());
        request.setAccessExpiresAt(issued.expiresAt());
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
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Request already decided");
        }

        Admin admin = adminRepository.findById(UUID.fromString(principal.getName())).orElseThrow();
        request.setStatus(RecordingRequestStatus.DENIED);
        request.setDecidedAt(Instant.now());
        request.setDecidedByAdmin(admin);
        recordingRequestRepository.save(request);

        return ResponseEntity.ok(QueueItem.from(request));
    }
}
