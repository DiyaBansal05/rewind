package com.recordingportal.backend.zoom;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Proxy-streams a Zoom recording's video bytes through this backend, given
 * a fresh download URL from Zoom -- the Zoom bearer token never reaches the
 * client this way. Shared by the student redemption flow (RecordingAccessController's
 * /r/{token}) and the admin candidate-preview flow (/r/preview/{token}), since
 * both need the exact same "authenticate to Zoom, fetch, relay bytes" logic.
 *
 * Known simplification: no HTTP Range support yet, so browser seek/scrub on
 * the video player won't be as smooth as native Zoom playback.
 */
@Service
public class ZoomRecordingStreamer {

    private static final Logger log = LoggerFactory.getLogger(ZoomRecordingStreamer.class);

    private final ZoomOAuthTokenService zoomOAuthTokenService;

    public ZoomRecordingStreamer(ZoomOAuthTokenService zoomOAuthTokenService) {
        this.zoomOAuthTokenService = zoomOAuthTokenService;
    }

    public void stream(String downloadUrl, HttpServletResponse servletResponse) throws IOException {
        String zoomToken = zoomOAuthTokenService.getAccessToken();

        servletResponse.setContentType("video/mp4");
        // Zoom's download_url commonly 3xx-redirects to the actual CDN location;
        // HttpClient.newHttpClient()'s default redirect policy is NEVER, which
        // would silently hand back an empty/redirect body instead of the video.
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest zoomRequest = HttpRequest.newBuilder(URI.create(downloadUrl))
                .header("Authorization", "Bearer " + zoomToken)
                .GET()
                .build();
        try {
            HttpResponse<InputStream> zoomResponse = client.send(zoomRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (zoomResponse.statusCode() != 200) {
                log.error("Zoom download returned status {}", zoomResponse.statusCode());
            }
            try (InputStream in = zoomResponse.body(); OutputStream out = servletResponse.getOutputStream()) {
                in.transferTo(out);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while streaming recording", e);
        }
    }
}
