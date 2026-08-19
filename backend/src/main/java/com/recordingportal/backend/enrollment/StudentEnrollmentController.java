package com.recordingportal.backend.enrollment;

import com.recordingportal.backend.batch.Batch;
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

    public record EnrolledBatch(UUID batchId, String batchName, String courseName) {
        static EnrolledBatch from(Batch b) {
            return new EnrolledBatch(b.getId(), b.getName(), b.getCourseName());
        }
    }

    @GetMapping
    public List<EnrolledBatch> myBatches(Principal principal) {
        UUID studentId = UUID.fromString(principal.getName());
        return enrollmentRepository.findWithBatchByStudentId(studentId).stream()
                .map(e -> EnrolledBatch.from(e.getBatch()))
                .toList();
    }
}
