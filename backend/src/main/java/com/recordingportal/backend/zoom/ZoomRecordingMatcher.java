package com.recordingportal.backend.zoom;

import com.recordingportal.backend.batch.Batch;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Matches a recording request (batch + missed class date) against the real
 * Zoom recordings found for that date. Every batch shares one Zoom account,
 * so a single day can have multiple recordings (different batches at
 * different times) -- this narrows candidates to actual video files whose
 * start time falls within the batch's scheduled class window (+/- a
 * tolerance for late starts), and never auto-picks when the result is
 * ambiguous.
 */
@Service
public class ZoomRecordingMatcher {

    private static final int TOLERANCE_MINUTES = 15;

    private final ZoomRecordingLookupService lookupService;
    private final ZoneId zoneId;

    public ZoomRecordingMatcher(
            ZoomRecordingLookupService lookupService,
            @Value("${app.institute.timezone}") String timezone) {
        this.lookupService = lookupService;
        this.zoneId = ZoneId.of(timezone);
    }

    public record MatchResult(List<ZoomRecordingLookupService.RecordingFileCandidate> candidates) {
        public boolean isUnambiguous() {
            return candidates.size() == 1;
        }
    }

    public MatchResult match(Batch batch, LocalDate classDate) {
        List<ZoomRecordingLookupService.RecordingFileCandidate> all = lookupService.findCandidates(classDate);

        LocalTime windowStart = batch.getClassStartTime().minusMinutes(TOLERANCE_MINUTES);
        LocalTime windowEnd = batch.getClassEndTime().plusMinutes(TOLERANCE_MINUTES);

        List<ZoomRecordingLookupService.RecordingFileCandidate> matched = all.stream()
                .filter(c -> "MP4".equalsIgnoreCase(c.fileType()))
                .filter(c -> {
                    ZonedDateTime local = c.startTime().atZone(zoneId);
                    if (!local.toLocalDate().equals(classDate)) return false;
                    LocalTime time = local.toLocalTime();
                    return !time.isBefore(windowStart) && !time.isAfter(windowEnd);
                })
                .toList();

        return new MatchResult(matched);
    }
}
