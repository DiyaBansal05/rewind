package com.recordingportal.backend.enrollment;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/batches")
public class StudentEnrollmentController {

    private final EnrollmentRepository enrollmentRepository;

    public StudentEnrollmentController(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    public record EnrolledBatch(UUID batchId, String batchName, String courseName, EnrollmentStatus status) {
        static EnrolledBatch from(Enrollment e) {
            return new EnrolledBatch(e.getBatch().getId(), e.getBatch().getName(), e.getBatch().getCourseName(), e.getStatus());
        }
    }

    /** Returns both APPROVED batches and still-PENDING join requests (but not DENIED ones)
     *  so the dashboard can show a "waiting for approval" state instead of just silently
     *  looking empty while a request is under review. */
    @GetMapping
    public List<EnrolledBatch> myBatches(Principal principal) {
        UUID studentId = UUID.fromString(principal.getName());
        return enrollmentRepository.findWithBatchByStudentId(studentId).stream()
                .filter(e -> e.getStatus() != EnrollmentStatus.DENIED)
                .map(EnrolledBatch::from)
                .toList();
    }
}
