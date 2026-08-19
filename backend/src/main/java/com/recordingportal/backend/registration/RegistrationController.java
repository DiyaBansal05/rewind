package com.recordingportal.backend.registration;

import com.recordingportal.backend.batch.Batch;
import com.recordingportal.backend.batch.BatchRepository;
import com.recordingportal.backend.enrollment.Enrollment;
import com.recordingportal.backend.enrollment.EnrollmentRepository;
import com.recordingportal.backend.security.JwtService;
import com.recordingportal.backend.security.Role;
import com.recordingportal.backend.student.Student;
import com.recordingportal.backend.student.StudentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/register")
public class RegistrationController {

    private final QrTokenService qrTokenService;
    private final BatchRepository batchRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final JwtService jwtService;

    public RegistrationController(
            QrTokenService qrTokenService,
            BatchRepository batchRepository,
            StudentRepository studentRepository,
            EnrollmentRepository enrollmentRepository,
            JwtService jwtService) {
        this.qrTokenService = qrTokenService;
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.jwtService = jwtService;
    }

    public record BatchInfoResponse(String batchName, String courseName) {
    }

    public record RegisterRequest(@NotBlank String token, @NotBlank String name, @NotBlank String phoneNumber) {
    }

    public record RegisterResponse(String token, boolean alreadyEnrolled) {
    }

    @GetMapping("/batch-info")
    public ResponseEntity<?> batchInfo(@RequestParam String token) {
        Batch batch = qrTokenService.parseBatchId(token).flatMap(batchRepository::findById).orElse(null);
        if (batch == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired registration link");
        }
        return ResponseEntity.ok(new BatchInfoResponse(batch.getName(), batch.getCourseName()));
    }

    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        var batchId = qrTokenService.parseBatchId(request.token());
        if (batchId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired registration link");
        }
        Batch batch = batchRepository.findById(batchId.get()).orElse(null);
        if (batch == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Batch no longer exists");
        }

        Student student = studentRepository.findByPhoneNumber(request.phoneNumber())
                .orElseGet(() -> {
                    Student s = new Student();
                    s.setName(request.name());
                    s.setPhoneNumber(request.phoneNumber());
                    return studentRepository.save(s);
                });

        boolean alreadyEnrolled = enrollmentRepository.existsByStudentIdAndBatchId(student.getId(), batch.getId());
        if (!alreadyEnrolled) {
            Enrollment enrollment = new Enrollment();
            enrollment.setStudent(student);
            enrollment.setBatch(batch);
            enrollmentRepository.save(enrollment);
        }

        String jwt = jwtService.issueToken(student.getId(), Role.STUDENT);
        return ResponseEntity.ok(new RegisterResponse(jwt, alreadyEnrolled));
    }
}
