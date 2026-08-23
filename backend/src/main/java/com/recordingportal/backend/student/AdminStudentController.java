package com.recordingportal.backend.student;

import com.recordingportal.backend.enrollment.EnrollmentRepository;
import com.recordingportal.backend.enrollment.EnrollmentStatus;
import com.recordingportal.backend.recording.RecordingRequestRepository;
import com.recordingportal.backend.recording.RecordingRequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the admin see a student's full recording-request history across all
 * their batches -- e.g. a student enrolled in two batches, and which batch
 * they've actually been requesting recordings for. Also surfaces a total
 * request count per student so the admin can spot who asks most often.
 */
@RestController
@RequestMapping("/api/admin/students")
public class AdminStudentController {

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final RecordingRequestRepository recordingRequestRepository;

    public AdminStudentController(
            StudentRepository studentRepository,
            EnrollmentRepository enrollmentRepository,
            RecordingRequestRepository recordingRequestRepository) {
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.recordingRequestRepository = recordingRequestRepository;
    }

    public record BatchEnrollment(UUID batchId, String batchName) {
    }

    public record StudentSummary(UUID id, String name, String phoneNumber, List<BatchEnrollment> batches, long totalRequests) {
    }

    public record RequestDetail(UUID id, UUID batchId, String batchName, LocalDate classDate, RecordingRequestStatus status, Instant requestedAt) {
    }

    @GetMapping
    public List<StudentSummary> list() {
        return studentRepository.findAll().stream()
                .map(s -> {
                    List<BatchEnrollment> batches = enrollmentRepository.findWithBatchByStudentId(s.getId()).stream()
                            .filter(e -> e.getStatus() == EnrollmentStatus.APPROVED)
                            .map(e -> new BatchEnrollment(e.getBatch().getId(), e.getBatch().getName()))
                            .toList();
                    long total = recordingRequestRepository.countByStudentId(s.getId());
                    return new StudentSummary(s.getId(), s.getName(), s.getPhoneNumber(), batches, total);
                })
                .toList();
    }

    @GetMapping("/{id}/recording-requests")
    public List<RequestDetail> requests(@PathVariable UUID id) {
        return recordingRequestRepository.findByStudentIdOrderByRequestedAtDesc(id).stream()
                .map(r -> new RequestDetail(r.getId(), r.getBatch().getId(), r.getBatch().getName(), r.getClassDate(), r.getStatus(), r.getRequestedAt()))
                .toList();
    }
}
