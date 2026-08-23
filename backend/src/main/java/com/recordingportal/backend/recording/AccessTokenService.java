package com.recordingportal.backend.recording;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues the signed, time-limited token embedded in a recording's access
 * link. Only the SHA-256 hash of the token is ever persisted (see
 * RecordingRequest.accessTokenHash) so a database leak alone can't hand out
 * live access.
 */
@Service
public class AccessTokenService {

    /** Admin candidate previews are a "decide right now" UI, not something meant
     *  to be saved/shared like a student's access link -- short and fixed rather
     *  than configurable. */
    private static final Duration PREVIEW_EXPIRATION = Duration.ofMinutes(15);

    private final Key signingKey;
    private final Duration expiration;

    public AccessTokenService(
            @Value("${app.access.secret}") String secret,
            @Value("${app.access.expiration-hours}") long expirationHours) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofHours(expirationHours);
    }

    public record IssuedToken(String rawToken, String hash, Instant expiresAt) {
    }

    public IssuedToken issue(UUID recordingRequestId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);
        String token = Jwts.builder()
                .subject(recordingRequestId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, hash(token), expiresAt);
    }

    public Optional<UUID> parseRecordingRequestId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record IssuedPreviewToken(String rawToken, Instant expiresAt) {
    }

    public record PreviewClaims(String meetingUuid, String recordingFileId) {
    }

    /** Unlike issue(), this isn't tied to a RecordingRequest row at all -- it's
     *  scoped directly to a specific Zoom (meetingUuid, recordingFileId) pair,
     *  since at MULTIPLE_CANDIDATES time no RecordingRequest has been decided
     *  yet (that's the whole point -- the admin hasn't chosen which one is
     *  correct). Claims are carried in the token itself rather than looked up
     *  in the database, since there's nothing to look up yet. */
    public IssuedPreviewToken issuePreview(String meetingUuid, String recordingFileId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(PREVIEW_EXPIRATION);
        String token = Jwts.builder()
                .claim("meetingUuid", meetingUuid)
                .claim("recordingFileId", recordingFileId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedPreviewToken(token, expiresAt);
    }

    public Optional<PreviewClaims> parsePreviewToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String meetingUuid = claims.get("meetingUuid", String.class);
            String recordingFileId = claims.get("recordingFileId", String.class);
            if (meetingUuid == null || recordingFileId == null) {
                return Optional.empty();
            }
            return Optional.of(new PreviewClaims(meetingUuid, recordingFileId));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
