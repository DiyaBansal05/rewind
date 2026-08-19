package com.recordingportal.backend.recording;

import com.recordingportal.backend.batch.Batch;
import com.recordingportal.backend.batch.BatchRepository;
import com.recordingportal.backend.enrollment.EnrollmentRepository;
import com.recordingportal.backend.notification.NotificationService;
import com.recordingportal.backend.student.Student;
import com.recordingportal.backend.student.StudentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

    public StudentRecordingController(
            RecordingRequestRepository recordingRequestRepository,
            StudentRepository studentRepository,
            BatchRepository batchRepository,
            EnrollmentRepository enrollmentRepository,
            NotificationService notificationService) {
        this.recordingRequestRepository = recordingRequestRepository;
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.notificationService = notificationService;
    }

    public record CreateRequest(@NotNull UUID batchId, @NotNull LocalDate classDate) {
    }

    public record RecordingRequestResponse(
            UUID id, UUID batchId, String batchName, LocalDate classDate,
            RecordingRequestStatus status, java.time.Instant requestedAt) {
        static RecordingRequestResponse from(RecordingRequest r) {
            return new RecordingRequestResponse(
                    r.getId(), r.getBatch().getId(), r.getBatch().getName(),
                    r.getClassDate(), r.getStatus(), r.getRequestedAt());
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateRequest request, java.security.Principal principal) {
        UUID studentId = UUID.fromString(principal.getName());
        Student student = studentRepository.findById(studentId).orElseThrow();

        if (!enrollmentRepository.existsByStudentIdAndBatchId(studentId, request.batchId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You're not enrolled in this batch");
        }
        Batch batch = batchRepository.findById(request.batchId()).orElseThrow();

        RecordingRequest recordingRequest = new RecordingRequest();
        recordingRequest.setStudent(student);
        recordingRequest.setBatch(batch);
        recordingRequest.setClassDate(request.classDate());
        recordingRequestRepository.save(recordingRequest);

        notificationService.notifyAdminRequestRaised(recordingRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(RecordingRequestResponse.from(recordingRequest));
    }

    @GetMapping
    public List<RecordingRequestResponse> history(java.security.Principal principal) {
        UUID studentId = UUID.fromString(principal.getName());
        return recordingRequestRepository.findByStudentIdOrderByRequestedAtDesc(studentId).stream()
                .map(RecordingRequestResponse::from)
                .toList();
    }
}
