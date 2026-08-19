package com.recordingportal.backend.recording;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public redemption endpoint for a recording access link. This token IS the
 * auth for this one resource -- a student may click it from a WhatsApp
 * message without an active portal session, so this is a standalone page
 * (not part of the React SPA -- a video/media link shouldn't route through
 * a client-side app framework anyway).
 *
 * Phase 1 stub: once the token/status/expiry all check out, this returns a
 * placeholder page instead of a real video. Phase 2 replaces the placeholder
 * with a fresh Zoom lookup + proxy-streamed video, without changing this
 * validation logic.
 */
@RestController
public class RecordingAccessController {

    private final AccessTokenService accessTokenService;
    private final RecordingRequestRepository recordingRequestRepository;
    private final AccessEventRepository accessEventRepository;

    public RecordingAccessController(
            AccessTokenService accessTokenService,
            RecordingRequestRepository recordingRequestRepository,
            AccessEventRepository accessEventRepository) {
        this.accessTokenService = accessTokenService;
        this.recordingRequestRepository = recordingRequestRepository;
        this.accessEventRepository = accessEventRepository;
    }

    @GetMapping(value = "/r/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> redeem(@PathVariable String token) {
        var recordingRequestId = accessTokenService.parseRecordingRequestId(token);
        RecordingRequest request = recordingRequestId
                .flatMap(recordingRequestRepository::findWithDetailsById)
                .orElse(null);

        if (recordingRequestId.isEmpty()) {
            return page(HttpStatus.NOT_FOUND, "Link not found", "This link doesn't look right. Double-check the URL you were sent.");
        }

        String tokenHash = accessTokenService.hash(token);
        boolean valid = request != null
                && request.getStatus() == RecordingRequestStatus.APPROVED
                && tokenHash.equals(request.getAccessTokenHash())
                && request.getAccessExpiresAt() != null
                && request.getAccessExpiresAt().isAfter(Instant.now());

        if (request != null) {
            AccessEvent event = new AccessEvent();
            event.setRecordingRequest(request);
            event.setSuccess(valid);
            accessEventRepository.save(event);
        }

        if (!valid) {
            return page(HttpStatus.GONE, "Link expired or revoked",
                    "This recording link is no longer valid. Ask your instructor to approve a new request if you still need it.");
        }

        String body = """
                <p class="label">%s &middot; %s</p>
                <h1>Recording ready</h1>
                <p>Real playback arrives in Phase 2 &mdash; for now this confirms the link, token, and expiry all check out end to end.</p>
                <p class="expiry">Access expires %s</p>
                """.formatted(escape(request.getBatch().getName()), request.getClassDate(), request.getAccessExpiresAt());
        return ResponseEntity.ok(html(body));
    }

    private ResponseEntity<String> page(HttpStatus status, String title, String message) {
        String body = "<h1>%s</h1><p>%s</p>".formatted(escape(title), escape(message));
        return ResponseEntity.status(status).body(html(body));
    }

    private String html(String bodyContent) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Recording Access</title>
                <style>
                  body { font-family: system-ui, sans-serif; background: #f8fafc; color: #0f172a;
                         display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }
                  .card { background: white; border: 1px solid #e2e8f0; border-radius: 12px; padding: 2.5rem;
                          max-width: 28rem; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
                  h1 { font-size: 1.25rem; margin: 0 0 0.75rem; }
                  p { line-height: 1.5; color: #475569; margin: 0.5rem 0; }
                  .label { text-transform: uppercase; font-size: 0.75rem; letter-spacing: 0.05em;
                            color: #64748b; margin: 0 0 0.25rem; }
                  .expiry { font-size: 0.8rem; color: #94a3b8; margin-top: 1rem; }
                </style>
                </head>
                <body><div class="card">%s</div></body>
                </html>
                """.formatted(bodyContent);
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
