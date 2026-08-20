package com.recordingportal.backend.zoom;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Read-only lookups against Zoom's cloud recordings. Every call here is a
 * GET -- nothing in this class (or anywhere in the app) calls Zoom's
 * recording delete/rename/settings endpoints.
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
            String meetingUuid, String meetingTopic, String recordingFileId,
            String fileType, Instant startTime, String downloadUrl) {
    }

    /**
     * Lists every recording file for the configured Zoom user on exactly
     * one date -- used to find candidates for a brand-new approval.
     * Hard-guarded to a recent rolling window (app.zoom.max-lookback-days,
     * default 7): this account has a lot of real, important recordings, so
     * we deliberately refuse to query anything outside a narrow, recent
     * range rather than trust every caller to pass a sane date. Throws
     * ZoomLookupNotAllowedException outside that window -- callers must not
     * catch-and-ignore that, it's a deliberate hard stop.
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

        return toCandidates(response);
    }

    /**
     * Re-fetches one already-matched meeting's recordings by UUID (not a
     * date-range scan -- inherently scoped to a single specific meeting
     * instance that was already selected during approval), and returns a
     * fresh download URL for the given file. Used at redemption time since
     * Zoom's download URLs shouldn't be cached long-term.
     */
    public Optional<RecordingFileCandidate> getFreshFile(String meetingUuid, String recordingFileId) {
        String token = tokenService.getAccessToken();
        log.info("Re-fetching Zoom recording file {} for meeting {}", recordingFileId, meetingUuid);

        ZoomMeeting meeting = restClient.get()
                .uri("/meetings/{uuid}/recordings", prepareUuidForPath(meetingUuid))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(ZoomMeeting.class);

        if (meeting == null || meeting.recordingFiles() == null) {
            return Optional.empty();
        }
        return meeting.recordingFiles().stream()
                .filter(f -> recordingFileId.equals(f.id()))
                .findFirst()
                .map(f -> new RecordingFileCandidate(
                        meeting.uuid(), meeting.topic(), f.id(), f.fileType(), f.recordingStart(), f.downloadUrl()));
    }

    private List<RecordingFileCandidate> toCandidates(ZoomRecordingsResponse response) {
        if (response == null || response.meetings() == null) {
            return List.of();
        }
        List<RecordingFileCandidate> candidates = new ArrayList<>();
        for (ZoomMeeting meeting : response.meetings()) {
            if (meeting.recordingFiles() == null) continue;
            for (ZoomRecordingFile file : meeting.recordingFiles()) {
                candidates.add(new RecordingFileCandidate(
                        meeting.uuid(), meeting.topic(), file.id(), file.fileType(), file.recordingStart(), file.downloadUrl()));
            }
        }
        return candidates;
    }

    /**
     * Zoom requires meeting UUIDs to be double URL-encoded in path segments,
     * but only when they contain '/' or start with '//' -- RestClient's URI
     * template substitution already applies one automatic encoding pass, so
     * for those UUIDs we pre-encode exactly once more here (giving two
     * passes total). A plain UUID with no special characters is passed
     * through as-is and gets just RestClient's single automatic pass,
     * matching what Zoom expects for the common case.
     */
    private String prepareUuidForPath(String uuid) {
        if (uuid.startsWith("/") || uuid.contains("//")) {
            return URLEncoder.encode(uuid, StandardCharsets.UTF_8);
        }
        return uuid;
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
            String uuid, String topic,
            @JsonProperty("recording_files") List<ZoomRecordingFile> recordingFiles) {
    }

    private record ZoomRecordingFile(
            String id,
            @JsonProperty("file_type") String fileType,
            @JsonProperty("recording_start") Instant recordingStart,
            @JsonProperty("download_url") String downloadUrl) {
    }
}
