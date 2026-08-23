package com.recordingportal.backend.enrollment;

import com.recordingportal.backend.admin.Admin;
import com.recordingportal.backend.admin.AdminRepository;
import com.recordingportal.backend.notification.NotificationService;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-facing review queue for batch join requests raised when a student
 * scans a QR code (see RegistrationController), plus the ability to remove
 * an already-approved student from a batch's roster.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminEnrollmentController {

    private final EnrollmentRepository enrollmentRepository;
    private final AdminRepository adminRepository;
    private final NotificationService notificationService;

    public AdminEnrollmentController(
            EnrollmentRepository enrollmentRepository,
            AdminRepository adminRepository,
            NotificationService notificationService) {
        this.enrollmentRepository = enrollmentRepository;
        this.adminRepository = adminRepository;
        this.notificationService = notificationService;
    }

    public record EnrollmentRequestItem(
            UUID id, String studentName, String studentPhone,
            UUID batchId, String batchName, String courseName, Instant requestedAt) {
        static EnrollmentRequestItem from(Enrollment e) {
            return new EnrollmentRequestItem(
                    e.getId(), e.getStudent().getName(), e.getStudent().getPhoneNumber(),
                    e.getBatch().getId(), e.getBatch().getName(), e.getBatch().getCourseName(), e.getEnrolledAt());
        }
    }

    @GetMapping("/enrollment-requests")
    public List<EnrollmentRequestItem> pending() {
        return enrollmentRepository.findWithDetailsByStatusOrderByEnrolledAtAsc(EnrollmentStatus.PENDING).stream()
                .map(EnrollmentRequestItem::from)
                .toList();
    }

    @PostMapping("/enrollment-requests/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable UUID id, Principal principal) {
        Enrollment enrollment = enrollmentRepository.findWithDetailsById(id).orElse(null);
        if (enrollment == null) {
            return ResponseEntity.notFound().build();
        }
        if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("reason", "ALREADY_DECIDED"));
        }

        Admin admin = adminRepository.findById(UUID.fromString(principal.getName())).orElseThrow();
        enrollment.setStatus(EnrollmentStatus.APPROVED);
        enrollment.setDecidedAt(Instant.now());
        enrollment.setDecidedByAdmin(admin);
        enrollmentRepository.save(enrollment);

        notificationService.notifyStudentEnrollmentApproved(enrollment);
        return ResponseEntity.ok(EnrollmentRequestItem.from(enrollment));
    }

    @PostMapping("/enrollment-requests/{id}/deny")
    public ResponseEntity<?> deny(@PathVariable UUID id, Principal principal) {
        Enrollment enrollment = enrollmentRepository.findWithDetailsById(id).orElse(null);
        if (enrollment == null) {
            return ResponseEntity.notFound().build();
        }
        if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("reason", "ALREADY_DECIDED"));
        }

        Admin admin = adminRepository.findById(UUID.fromString(principal.getName())).orElseThrow();
        enrollment.setStatus(EnrollmentStatus.DENIED);
        enrollment.setDecidedAt(Instant.now());
        enrollment.setDecidedByAdmin(admin);
        enrollmentRepository.save(enrollment);

        return ResponseEntity.ok(EnrollmentRequestItem.from(enrollment));
    }

    /** Removes a student from a batch entirely (deletes the enrollment row), used from the
     *  per-batch roster view. Deleting rather than soft-denying means the unique
     *  (student, batch) constraint doesn't block them from being re-invited/re-registering
     *  later. */
    @DeleteMapping("/enrollments/{id}")
    public ResponseEntity<?> remove(@PathVariable UUID id) {
        if (!enrollmentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        enrollmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
