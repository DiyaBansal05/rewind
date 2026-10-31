package com.recordingportal.backend.recording;

import com.recordingportal.backend.recording.WatchProgressService.LinkState;
import com.recordingportal.backend.recording.WatchProgressService.ProgressPing;
import com.recordingportal.backend.recording.WatchProgressService.Resolved;
import com.recordingportal.backend.zoom.ZoomRecordingLookupService;
import com.recordingportal.backend.zoom.ZoomRecordingStreamer;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public redemption endpoints for recording access links. The token in the
 * URL IS the auth for that one resource -- clicked from a WhatsApp message
 * or the student dashboard without necessarily an active portal session, so
 * these are standalone routes served by the backend, not part of the SPA.
 *
 *  - GET  /r/{token}           player page (logs one "open" per visit)
 *  - GET  /r/{token}/video     the video bytes, Range-aware, proxied from Zoom
 *  - POST /r/{token}/progress  playback progress pings from the player page
 *  - GET  /r/preview/{token}   admin preview of a MULTIPLE_CANDIDATES option
 *
 * Every request re-checks the link against the database, so revoking or
 * letting a link expire stops playback on the player's next range request.
 * The Zoom bearer token never reaches the client.
 */
@RestController
public class RecordingAccessController {

    private static final Logger log = LoggerFactory.getLogger(RecordingAccessController.class);

    private final WatchProgressService watchProgressService;
    private final AccessTokenService accessTokenService;
    private final ZoomRecordingLookupService zoomRecordingLookupService;
    private final ZoomRecordingStreamer zoomRecordingStreamer;
    private final ZoneId zoneId;
    private final String playerTemplate;

    public RecordingAccessController(
            WatchProgressService watchProgressService,
            AccessTokenService accessTokenService,
            ZoomRecordingLookupService zoomRecordingLookupService,
            ZoomRecordingStreamer zoomRecordingStreamer,
            @Value("${app.institute.timezone}") String timezone) {
        this.watchProgressService = watchProgressService;
        this.accessTokenService = accessTokenService;
        this.zoomRecordingLookupService = zoomRecordingLookupService;
        this.zoomRecordingStreamer = zoomRecordingStreamer;
        this.zoneId = ZoneId.of(timezone);
        this.playerTemplate = loadTemplate("player.html");
    }

    @GetMapping("/r/{token}")
    public ResponseEntity<String> player(
            @PathVariable String token,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        Resolved resolved = watchProgressService.resolve(token);
        if (resolved.state() == LinkState.UNKNOWN) {
            return htmlPage(HttpStatus.NOT_FOUND, "Link not found", "This link doesn't look right. Double-check the URL you were sent.");
        }
        boolean valid = resolved.state() == LinkState.VALID;
        AccessEvent session = watchProgressService.recordOpen(resolved.request(), valid, userAgent);
        if (!valid) {
            return htmlPage(HttpStatus.GONE, "Link expired or revoked",
                    "This recording link is no longer valid. Ask your instructor to approve a new request if you still need it.");
        }

        RecordingRequest r = resolved.request();
        String title = r.getBatch().getName();
        String classDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH).format(r.getClassDate());
        String expires = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH).format(r.getAccessExpiresAt().atZone(zoneId));
        String config = """
                {"videoUrl":"%s","progressUrl":"%s","sessionId":"%s","resumeAt":%d}"""
                .formatted("/r/" + token + "/video", "/r/" + token + "/progress", session.getId(), r.getLastPositionSeconds());

        String page = playerTemplate
                .replace("{{TITLE}}", escape(title))
                .replace("{{CLASS_DATE}}", escape(classDate))
                .replace("{{EXPIRES}}", escape(expires))
                .replace("{{CONFIG}}", config);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(page);
    }

    @GetMapping("/r/{token}/video")
    public void video(
            @PathVariable String token,
            @RequestHeader(value = "Range", required = false) String range,
            HttpServletResponse response) throws IOException {
        Resolved resolved = watchProgressService.resolve(token);
        if (resolved.state() != LinkState.VALID) {
            response.sendError(HttpServletResponse.SC_GONE);
            return;
        }
        RecordingRequest r = resolved.request();
        Optional<ZoomRecordingLookupService.RecordingFileCandidate> fresh = Optional.empty();
        try {
            fresh = zoomRecordingLookupService.getFreshFileCached(r.getZoomMeetingUuid(), r.getZoomRecordingFileId());
        } catch (Exception e) {
            log.error("Failed to re-fetch Zoom recording for request {}", r.getId(), e);
        }
        if (fresh.isEmpty()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        zoomRecordingStreamer.stream(fresh.get().downloadUrl(), range, response);
    }

    @PostMapping("/r/{token}/progress")
    public ResponseEntity<Void> progress(@PathVariable String token, @RequestBody ProgressPing ping) {
        boolean live = watchProgressService.recordProgress(token, ping);
        return live ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.GONE).build();
    }

    /**
     * Admin-facing preview of one MULTIPLE_CANDIDATES option, before they've
     * decided which recording to actually approve for the student. Token is
     * issued alongside the candidate list itself (see AdminRecordingController.CandidateView)
     * and is short-lived -- this is meant to be opened once, right after
     * approval hits an ambiguous match, not saved or shared.
     */
    @GetMapping("/r/preview/{token}")
    public ResponseEntity<?> preview(
            @PathVariable String token,
            @RequestHeader(value = "Range", required = false) String range,
            HttpServletResponse servletResponse) throws IOException {
        var claims = accessTokenService.parsePreviewToken(token);
        if (claims.isEmpty()) {
            return htmlPage(HttpStatus.NOT_FOUND, "Link not found", "This preview link doesn't look right or has expired.");
        }

        Optional<ZoomRecordingLookupService.RecordingFileCandidate> fresh;
        try {
            fresh = zoomRecordingLookupService.getFreshFileCached(claims.get().meetingUuid(), claims.get().recordingFileId());
        } catch (Exception e) {
            log.error("Failed to re-fetch Zoom recording for preview", e);
            fresh = Optional.empty();
        }

        if (fresh.isEmpty()) {
            return htmlPage(HttpStatus.SERVICE_UNAVAILABLE, "Recording temporarily unavailable",
                    "We couldn't reach the recording right now. Try again in a bit.");
        }

        // Written straight to the servlet response: this method's other
        // branches return HTML pages, and a ResponseEntity<?> return type
        // isn't recognized by Spring as a streaming body.
        zoomRecordingStreamer.stream(fresh.get().downloadUrl(), range, servletResponse);
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
                         display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 1rem; box-sizing: border-box; }
                  .card { background: white; border: 1px solid #e2e8f0; border-radius: 12px; padding: 2rem;
                          max-width: 28rem; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
                  h1 { font-size: 1.25rem; margin: 0 0 0.75rem; }
                  p { line-height: 1.5; color: #475569; margin: 0.5rem 0; }
                </style>
                </head>
                <body><div class="card">%s</div></body>
                </html>
                """.formatted(bodyContent);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String loadTemplate(String name) {
        try (InputStream in = new ClassPathResource("player/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing player template " + name, e);
        }
    }
}
