package com.recordingportal.backend.student;

import com.recordingportal.backend.security.JwtService;
import com.recordingportal.backend.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 1 simplification: login is phone-number-only, no OTP. There is no
 * delivery channel to a student yet (WhatsApp only reaches the admin until
 * Phase 3), so there's nowhere to send a verification code. This is a
 * deliberate, temporary tradeoff -- anyone who knows an enrolled student's
 * phone number can sign in as them. Upgrade to real OTP-via-WhatsApp when
 * Phase 3 lands.
 */
@RestController
@RequestMapping("/api/auth/student")
public class StudentAuthController {

    private final StudentRepository studentRepository;
    private final JwtService jwtService;

    public StudentAuthController(StudentRepository studentRepository, JwtService jwtService) {
        this.studentRepository = studentRepository;
        this.jwtService = jwtService;
    }

    public record LoginRequest(@NotBlank String phoneNumber) {
    }

    public record TokenResponse(String token) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Student student = studentRepository.findByPhoneNumber(request.phoneNumber()).orElse(null);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No enrollment found for this phone number. Register via your batch's QR code first.");
        }
        return ResponseEntity.ok(new TokenResponse(jwtService.issueToken(student.getId(), Role.STUDENT)));
    }
}
