package com.recordingportal.backend.admin;

import com.recordingportal.backend.security.JwtService;
import com.recordingportal.backend.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/admin")
public class AdminAuthController {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminAuthController(AdminRepository adminRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record TokenResponse(String token) {
    }

    /**
     * Only works while no admin account exists yet. First caller wins; the
     * endpoint self-disables afterward so it can't be used to add more admins.
     */
    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@RequestBody LoginRequest request) {
        if (adminRepository.count() > 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin already exists");
        }
        Admin admin = new Admin();
        admin.setEmail(request.email());
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        adminRepository.save(admin);
        return ResponseEntity.ok(new TokenResponse(jwtService.issueToken(admin.getId(), Role.ADMIN)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return adminRepository.findByEmail(request.email())
                .filter(admin -> passwordEncoder.matches(request.password(), admin.getPasswordHash()))
                .map(admin -> ResponseEntity.ok(new TokenResponse(jwtService.issueToken(admin.getId(), Role.ADMIN))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
