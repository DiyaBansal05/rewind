package com.recordingportal.backend.zoom;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Proxy-streams a Zoom recording's video bytes through this backend, given
 * a fresh download URL from Zoom -- the Zoom bearer token never reaches the
 * client this way. Shared by the student player (RecordingAccessController's
 * /r/{token}/video) and the admin candidate preview (/r/preview/{token}).
 *
 * HTTP Range requests are passed through to Zoom, so the browser's player
 * can seek and resume without downloading the whole file first.
 */
@Service
public class ZoomRecordingStreamer {

    private static final Logger log = LoggerFactory.getLogger(ZoomRecordingStreamer.class);

    /** Response headers worth relaying from Zoom for media playback. */
    private static final List<String> PASSTHROUGH_HEADERS = List.of("Content-Length", "Content-Range", "Accept-Ranges");

    private final ZoomOAuthTokenService zoomOAuthTokenService;
    // Zoom's download_url commonly 3xx-redirects to the actual CDN location;
    // HttpClient's default redirect policy is NEVER, which would silently hand
    // back an empty/redirect body instead of the video.
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ZoomRecordingStreamer(ZoomOAuthTokenService zoomOAuthTokenService) {
        this.zoomOAuthTokenService = zoomOAuthTokenService;
    }

    public void stream(String downloadUrl, String rangeHeader, HttpServletResponse servletResponse) throws IOException {
        String zoomToken = zoomOAuthTokenService.getAccessToken();

        HttpRequest.Builder zoomRequest = HttpRequest.newBuilder(URI.create(downloadUrl))
                .header("Authorization", "Bearer " + zoomToken)
                .GET();
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            zoomRequest.header("Range", rangeHeader);
        }

        HttpResponse<InputStream> zoomResponse;
        try {
            zoomResponse = client.send(zoomRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while streaming recording", e);
        }

        int status = zoomResponse.statusCode();
        if (status != 200 && status != 206) {
            log.error("Zoom download returned status {}", status);
            zoomResponse.body().close();
            servletResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY);
            return;
        }

        servletResponse.setStatus(status);
        servletResponse.setContentType("video/mp4");
        servletResponse.setHeader("Accept-Ranges", "bytes");
        servletResponse.setHeader("Cache-Control", "private, no-store");
        for (String name : PASSTHROUGH_HEADERS) {
            zoomResponse.headers().firstValue(name).ifPresent(v -> servletResponse.setHeader(name, v));
        }

        try (InputStream in = zoomResponse.body(); OutputStream out = servletResponse.getOutputStream()) {
            in.transferTo(out);
        } catch (IOException e) {
            // Browsers routinely abort range requests mid-flight when the
            // viewer seeks; that's normal, not an error worth a stack trace.
            log.debug("Recording stream ended early: {}", e.getMessage());
        }
    }
}
