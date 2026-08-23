package com.recordingportal.backend.batch;

import com.recordingportal.backend.enrollment.Enrollment;
import com.recordingportal.backend.enrollment.EnrollmentRepository;
import com.recordingportal.backend.enrollment.EnrollmentStatus;
import com.recordingportal.backend.registration.QrTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
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
@RequestMapping("/api/admin/batches")
public class BatchController {

    private final BatchRepository batchRepository;
    private final QrTokenService qrTokenService;
    private final EnrollmentRepository enrollmentRepository;

    public BatchController(BatchRepository batchRepository, QrTokenService qrTokenService, EnrollmentRepository enrollmentRepository) {
        this.batchRepository = batchRepository;
        this.qrTokenService = qrTokenService;
        this.enrollmentRepository = enrollmentRepository;
    }

    public record CreateBatchRequest(
            @NotBlank String name,
            @NotBlank String courseName,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotEmpty Set<DayOfWeek> classDaysOfWeek,
            @NotNull LocalTime classStartTime,
            @NotNull LocalTime classEndTime) {
    }

    public record BatchResponse(
            UUID id,
            String name,
            String courseName,
            LocalDate startDate,
            LocalDate endDate,
            Set<DayOfWeek> classDaysOfWeek,
            LocalTime classStartTime,
            LocalTime classEndTime,
            BatchStatus status) {
        static BatchResponse from(Batch b) {
            return new BatchResponse(b.getId(), b.getName(), b.getCourseName(), b.getStartDate(), b.getEndDate(),
                    b.getClassDaysOfWeek(), b.getClassStartTime(), b.getClassEndTime(), b.getStatus());
        }
    }

    public record RegistrationLinkResponse(String token, String registrationPath) {
    }

    public record EnrolledStudent(UUID enrollmentId, UUID studentId, String name, String phoneNumber) {
        static EnrolledStudent from(Enrollment e) {
            return new EnrolledStudent(e.getId(), e.getStudent().getId(), e.getStudent().getName(), e.getStudent().getPhoneNumber());
        }
    }

    @PostMapping
    public ResponseEntity<BatchResponse> create(@Valid @RequestBody CreateBatchRequest request) {
        Batch batch = new Batch();
        batch.setName(request.name());
        batch.setCourseName(request.courseName());
        batch.setStartDate(request.startDate());
        batch.setEndDate(request.endDate());
        batch.setClassDaysOfWeek(request.classDaysOfWeek());
        batch.setClassStartTime(request.classStartTime());
        batch.setClassEndTime(request.classEndTime());
        batchRepository.save(batch);
        return ResponseEntity.status(HttpStatus.CREATED).body(BatchResponse.from(batch));
    }

    @GetMapping
    public List<BatchResponse> list() {
        return batchRepository.findAll().stream().map(BatchResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchResponse> get(@PathVariable UUID id) {
        return batchRepository.findById(id)
                .map(b -> ResponseEntity.ok(BatchResponse.from(b)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/registration-link")
    public ResponseEntity<RegistrationLinkResponse> registrationLink(@PathVariable UUID id) {
        if (!batchRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        String token = qrTokenService.issueToken(id);
        return ResponseEntity.ok(new RegistrationLinkResponse(token, "/register?token=" + token));
    }

    /** The approved roster for a batch -- used by the admin's "students in this batch"
     *  view, which also lets them remove a student (see AdminEnrollmentController). */
    @GetMapping("/{id}/students")
    public List<EnrolledStudent> students(@PathVariable UUID id) {
        return enrollmentRepository.findWithStudentByBatchIdAndStatus(id, EnrollmentStatus.APPROVED).stream()
                .map(EnrolledStudent::from)
                .toList();
    }
}
