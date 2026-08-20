package com.recordingportal.backend.zoom;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Read-only lookups against Zoom's cloud recordings for one specific date.
 * Every call here is a GET -- nothing in this class (or anywhere in the app)
 * calls Zoom's recording delete/rename/settings endpoints.
 *
 * Hard-guarded to a recent rolling window (app.zoom.max-lookback-days,
 * default 7): this account has a lot of real, important recordings, so we
 * deliberately refuse to query anything outside a narrow, recent range
 * rather than trust every caller to pass a sane date.
 */
@Service
public class ZoomRecordingLookupService {

    private static final Logger log = LoggerFactory.getLogger(ZoomRecordingLookupService.class);

    private final ZoomOAuthTokenService tokenService;
    private final RestClient restClient;
    private final String userEmail;
    private final long maxLookbackDays;

    public ZoomRecordingLookupService(
            ZoomOAuthTokenService tokenService,
            @Value("${app.zoom.api-base-url}") String apiBaseUrl,
            @Value("${app.zoom.user-email}") String userEmail,
            @Value("${app.zoom.max-lookback-days}") long maxLookbackDays) {
        this.tokenService = tokenService;
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).build();
        this.userEmail = userEmail;
        this.maxLookbackDays = maxLookbackDays;
    }

    public record RecordingFileCandidate(
            String meetingId, String meetingTopic, String recordingFileId, Instant startTime, String downloadUrl) {
    }

    /**
     * Lists every recording file for the configured Zoom user on exactly
     * one date. Throws ZoomLookupNotAllowedException if that date falls
     * outside the safety window -- callers must not catch-and-ignore that,
     * it's a deliberate hard stop.
     */
    public List<RecordingFileCandidate> findCandidates(LocalDate classDate) {
        assertWithinSafetyWindow(classDate);

        String token = tokenService.getAccessToken();
        log.info("Querying Zoom recordings for {} (user={})", classDate, userEmail);

        ZoomRecordingsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/users/{userEmail}/recordings")
                        .queryParam("from", classDate)
                        .queryParam("to", classDate)
                        .build(userEmail))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(ZoomRecordingsResponse.class);

        if (response == null || response.meetings() == null) {
            return List.of();
        }

        List<RecordingFileCandidate> candidates = new ArrayList<>();
        for (ZoomMeeting meeting : response.meetings()) {
            if (meeting.recordingFiles() == null) continue;
            for (ZoomRecordingFile file : meeting.recordingFiles()) {
                candidates.add(new RecordingFileCandidate(
                        meeting.id(), meeting.topic(), file.id(), file.recordingStart(), file.downloadUrl()));
            }
        }
        return candidates;
    }

    private void assertWithinSafetyWindow(LocalDate classDate) {
        LocalDate today = LocalDate.now();
        LocalDate earliestAllowed = today.minusDays(maxLookbackDays);
        if (classDate.isBefore(earliestAllowed) || classDate.isAfter(today)) {
            throw new ZoomLookupNotAllowedException(
                    "Refusing to query Zoom for " + classDate + " -- only dates within the last "
                            + maxLookbackDays + " days are allowed by app.zoom.max-lookback-days.");
        }
    }

    private record ZoomRecordingsResponse(List<ZoomMeeting> meetings) {
    }

    private record ZoomMeeting(
            String id, String topic,
            @JsonProperty("recording_files") List<ZoomRecordingFile> recordingFiles) {
    }

    private record ZoomRecordingFile(
            String id,
            @JsonProperty("recording_start") Instant recordingStart,
            @JsonProperty("download_url") String downloadUrl) {
    }
}
