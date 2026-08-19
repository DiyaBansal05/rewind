package com.recordingportal.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final Key signingKey;
    private final Duration adminExpiration;
    private final Duration studentExpiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.admin-expiration-minutes}") long adminExpirationMinutes,
            @Value("${app.jwt.student-expiration-days}") long studentExpirationDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.adminExpiration = Duration.ofMinutes(adminExpirationMinutes);
        this.studentExpiration = Duration.ofDays(studentExpirationDays);
    }

    public String issueToken(UUID subjectId, Role role) {
        Duration expiration = role == Role.ADMIN ? adminExpiration : studentExpiration;
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subjectId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    public Optional<AuthenticatedPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID subjectId = UUID.fromString(claims.getSubject());
            Role role = Role.valueOf(claims.get("role", String.class));
            return Optional.of(new AuthenticatedPrincipal(subjectId, role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record AuthenticatedPrincipal(UUID id, Role role) {
    }
}
