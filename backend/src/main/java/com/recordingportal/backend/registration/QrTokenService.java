package com.recordingportal.backend.registration;

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

/**
 * Signs the per-batch registration token embedded in each QR code, kept on a
 * separate secret from student/admin auth tokens so the two concerns can't
 * compromise each other.
 */
@Service
public class QrTokenService {

    private final Key signingKey;

    public QrTokenService(@Value("${app.qr.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(UUID batchId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("batchId", batchId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofDays(365))))
                .signWith(signingKey)
                .compact();
    }

    public Optional<UUID> parseBatchId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(UUID.fromString(claims.get("batchId", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
