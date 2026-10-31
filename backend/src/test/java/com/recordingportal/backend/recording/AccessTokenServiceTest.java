package com.recordingportal.backend.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessTokenServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret";
    private final AccessTokenService service = new AccessTokenService(SECRET, 48);

    @Test
    void tokenCanBeRebuiltFromRequestWithoutStoringIt() {
        UUID id = UUID.randomUUID();
        Instant decidedAt = Instant.parse("2026-10-29T12:50:01.123456Z");
        var issued = service.issue(id, decidedAt);

        RecordingRequest request = new RecordingRequest();
        request.setId(id);
        request.setDecidedAt(decidedAt);
        request.setAccessTokenHash(issued.hash());

        assertEquals(Optional.of(issued.rawToken()), service.rebuildToken(request));
        assertEquals(decidedAt.plus(Duration.ofHours(48)), issued.expiresAt());
        assertEquals(Optional.of(id), service.parseRecordingRequestId(issued.rawToken()));
    }

    @Test
    void legacyTokenPastItsJwtExpiryStillIdentifiesTheRequest() {
        // Links sent before this change carry their own exp claim; the
        // database expiry now decides, so an extended request keeps working.
        UUID id = UUID.randomUUID();
        String legacy = Jwts.builder()
                .subject(id.toString())
                .issuedAt(Date.from(Instant.now().minus(Duration.ofDays(5))))
                .expiration(Date.from(Instant.now().minus(Duration.ofDays(3))))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertEquals(Optional.of(id), service.parseRecordingRequestId(legacy));
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() {
        String forged = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-another-secret".getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertTrue(service.parseRecordingRequestId(forged).isEmpty());
    }

    @Test
    void previewTokensAreNotAccessTokens() {
        String preview = service.issuePreview("meeting", "file").rawToken();
        assertTrue(service.parseRecordingRequestId(preview).isEmpty());
    }
}
