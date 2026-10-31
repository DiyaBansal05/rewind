package com.recordingportal.backend.zoom;

import com.recordingportal.backend.batch.Batch;
import com.recordingportal.backend.batch.ClassChangeRepository;
import com.recordingportal.backend.batch.ClassSchedule;
import com.recordingportal.backend.batch.ClassSchedule.ClassTime;
import com.recordingportal.backend.batch.ClassSchedule.NoClassReason;
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
 * start time falls within the class's time window on that date, and never
 * auto-picks when the result is ambiguous.
 *
 * The class time comes from ClassSchedule: an admin's change for that exact
 * date if there is one, otherwise the batch's regular slot for that weekday.
 *
 * The window starts exactly at the class start time (no early tolerance) --
 * back-to-back batches on the same Zoom account mean a few minutes of slack
 * before the start risks pulling in the tail of the *previous* class's
 * recording instead. It extends past the end time by END_TOLERANCE_MINUTES
 * to allow for classes that simply run long, which doesn't have the same
 * mixup risk since nothing else is expected to start recording during that
 * window. A class running past midnight matches recordings that started
 * any time from its start until the end of that day.
 */
@Service
public class ZoomRecordingMatcher {

    private static final int END_TOLERANCE_MINUTES = 15;

    private final ZoomRecordingLookupService lookupService;
    private final ClassChangeRepository classChangeRepository;
    private final ZoneId zoneId;

    public ZoomRecordingMatcher(
            ZoomRecordingLookupService lookupService,
            ClassChangeRepository classChangeRepository,
            @Value("${app.institute.timezone}") String timezone) {
        this.lookupService = lookupService;
        this.classChangeRepository = classChangeRepository;
        this.zoneId = ZoneId.of(timezone);
    }

    /**
     * @param classTime     when class ran that date; null if there was no class
     * @param noClassReason why there was no class, when classTime is null
     */
    public record MatchResult(
            List<ZoomRecordingLookupService.RecordingFileCandidate> candidates,
            ClassTime classTime, NoClassReason noClassReason) {
        public boolean isUnambiguous() {
            return candidates.size() == 1;
        }
    }

    public MatchResult match(Batch batch, LocalDate classDate) {
        var resolution = ClassSchedule.resolve(batch, classDate,
                classChangeRepository.findByBatchIdAndClassDate(batch.getId(), classDate));
        if (resolution.classTime().isEmpty()) {
            // No class that day, so don't even ask Zoom.
            return new MatchResult(List.of(), null, resolution.noClassReason());
        }
        ClassTime classTime = resolution.classTime().get();
        return new MatchResult(filter(lookupService.findCandidates(classDate), classTime, zoneId), classTime, null);
    }

    static List<ZoomRecordingLookupService.RecordingFileCandidate> filter(
            List<ZoomRecordingLookupService.RecordingFileCandidate> all, ClassTime classTime, ZoneId zoneId) {
        LocalTime windowStart = classTime.start();
        LocalTime lateEnd = classTime.end().plusMinutes(END_TOLERANCE_MINUTES);
        // Past midnight (either the class itself or the tolerance) -> rest of the day.
        LocalTime windowEnd = classTime.crossesMidnight() || lateEnd.isBefore(classTime.end()) ? LocalTime.MAX : lateEnd;

        return all.stream()
                .filter(c -> "MP4".equalsIgnoreCase(c.fileType()))
                .filter(c -> {
                    ZonedDateTime local = c.startTime().atZone(zoneId);
                    if (!local.toLocalDate().equals(classTime.date())) return false;
                    LocalTime time = local.toLocalTime();
                    return !time.isBefore(windowStart) && !time.isAfter(windowEnd);
                })
                .toList();
    }
}
