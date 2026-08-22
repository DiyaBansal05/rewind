package com.recordingportal.backend.student;

import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/me")
public class StudentProfileController {

    private final StudentRepository studentRepository;

    public StudentProfileController(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public record MeResponse(String name, String phoneNumber) {
    }

    @GetMapping
    public ResponseEntity<MeResponse> me(Principal principal) {
        UUID studentId = UUID.fromString(principal.getName());
        return studentRepository.findById(studentId)
                .map(s -> ResponseEntity.ok(new MeResponse(s.getName(), s.getPhoneNumber())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
