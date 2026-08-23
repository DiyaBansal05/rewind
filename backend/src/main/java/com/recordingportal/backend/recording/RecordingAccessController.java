package com.recordingportal.backend.recording;

import com.recordingportal.backend.zoom.ZoomRecordingLookupService;
import com.recordingportal.backend.zoom.ZoomRecordingStreamer;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public redemption endpoints for recording access links. The token in the
 * URL IS the auth for that one resource -- clicked from a WhatsApp message
 * or an admin's approval screen without necessarily an active portal
 * session, so these are standalone routes (not part of the React SPA -- a
 * video/media link shouldn't route through a client-side app framework
 * anyway).
 *
 * Two flavors share the same underlying mechanism (see ZoomRecordingStreamer):
 *  - /r/{token}: a student's approved recording, long-lived (app.access.expiration-hours).
 *  - /r/preview/{token}: an admin previewing a MULTIPLE_CANDIDATES option before
 *    picking which one to approve -- short-lived (see AccessTokenService.PREVIEW_EXPIRATION).
 *
 * Both fetch a *fresh* download URL from Zoom at redeem-time (never caching
 * Zoom's URL long-term) and proxy-stream the video through this backend --
 * the Zoom bearer token never reaches the client either way.
 *
 * Known simplification: no HTTP Range support yet, so browser seek/scrub on
 * the video player won't be as smooth as native Zoom playback. Fine for now;
 * would be the next thing to add if this becomes the primary way students
 * watch full lectures rather than quick catch-up viewing.
 */
@RestController
public class RecordingAccessController {

    private static final Logger log = LoggerFactory.getLogger(RecordingAccessController.class);

    private final AccessTokenService accessTokenService;
    private final RecordingRequestRepository recordingRequestRepository;
    private final AccessEventRepository accessEventRepository;
    private final ZoomRecordingLookupService zoomRecordingLookupService;
    private final ZoomRecordingStreamer zoomRecordingStreamer;

    public RecordingAccessController(
            AccessTokenService accessTokenService,
            RecordingRequestRepository recordingRequestRepository,
            AccessEventRepository accessEventRepository,
            ZoomRecordingLookupService zoomRecordingLookupService,
            ZoomRecordingStreamer zoomRecordingStreamer) {
        this.accessTokenService = accessTokenService;
        this.recordingRequestRepository = recordingRequestRepository;
        this.accessEventRepository = accessEventRepository;
        this.zoomRecordingLookupService = zoomRecordingLookupService;
        this.zoomRecordingStreamer = zoomRecordingStreamer;
    }

    @GetMapping("/r/{token}")
    public ResponseEntity<?> redeem(@PathVariable String token, HttpServletResponse servletResponse) throws IOException {
        var recordingRequestId = accessTokenService.parseRecordingRequestId(token);
        RecordingRequest request = recordingRequestId
                .flatMap(recordingRequestRepository::findWithDetailsById)
                .orElse(null);

        if (recordingRequestId.isEmpty()) {
            return htmlPage(HttpStatus.NOT_FOUND, "Link not found", "This link doesn't look right. Double-check the URL you were sent.");
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
            return htmlPage(HttpStatus.GONE, "Link expired or revoked",
                    "This recording link is no longer valid. Ask your instructor to approve a new request if you still need it.");
        }

        Optional<ZoomRecordingLookupService.RecordingFileCandidate> fresh = Optional.empty();
        try {
            fresh = zoomRecordingLookupService.getFreshFile(request.getZoomMeetingUuid(), request.getZoomRecordingFileId());
        } catch (Exception e) {
            log.error("Failed to re-fetch Zoom recording for request {}", request.getId(), e);
        }

        if (fresh.isEmpty()) {
            return htmlPage(HttpStatus.SERVICE_UNAVAILABLE, "Recording temporarily unavailable",
                    "We couldn't reach the recording right now. Try again in a bit, or ask your instructor if the problem persists.");
        }

        // Written directly to the raw servlet response rather than returned via
        // ResponseEntity<StreamingResponseBody>: Spring's streaming-body return
        // handler relies on the method's *declared* generic return type, and
        // this method's other branches return ResponseEntity<String> (HTML error
        // pages), so the combined signature erases to ResponseEntity<?> --
        // which Spring failed to recognize as a streaming case at runtime
        // (fell through to a plain HttpMessageConverter lookup and errored).
        // Writing to the servlet response directly sidesteps that entirely.
        zoomRecordingStreamer.stream(fresh.get().downloadUrl(), servletResponse);
        return null;
    }

    /**
     * Admin-facing preview of one MULTIPLE_CANDIDATES option, before they've
     * decided which recording to actually approve for the student. Token is
     * issued alongside the candidate list itself (see AdminRecordingController.CandidateView)
     * and is short-lived -- this is meant to be opened once, right after
     * approval hits an ambiguous match, not saved or shared.
     */
    @GetMapping("/r/preview/{token}")
    public ResponseEntity<?> preview(@PathVariable String token, HttpServletResponse servletResponse) throws IOException {
        var claims = accessTokenService.parsePreviewToken(token);
        if (claims.isEmpty()) {
            return htmlPage(HttpStatus.NOT_FOUND, "Link not found", "This preview link doesn't look right or has expired.");
        }

        Optional<ZoomRecordingLookupService.RecordingFileCandidate> fresh;
        try {
            fresh = zoomRecordingLookupService.getFreshFile(claims.get().meetingUuid(), claims.get().recordingFileId());
        } catch (Exception e) {
            log.error("Failed to re-fetch Zoom recording for preview", e);
            fresh = Optional.empty();
        }

        if (fresh.isEmpty()) {
            return htmlPage(HttpStatus.SERVICE_UNAVAILABLE, "Recording temporarily unavailable",
                    "We couldn't reach the recording right now. Try again in a bit.");
        }

        zoomRecordingStreamer.stream(fresh.get().downloadUrl(), servletResponse);
        return null;
    }

    private ResponseEntity<String> htmlPage(HttpStatus status, String title, String message) {
        String body = "<h1>%s</h1><p>%s</p>".formatted(escape(title), escape(message));
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html(body));
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
