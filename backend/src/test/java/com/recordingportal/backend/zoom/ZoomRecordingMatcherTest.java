package com.recordingportal.backend.zoom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.recordingportal.backend.batch.ClassSchedule.ClassTime;
import com.recordingportal.backend.batch.ClassSchedule.Source;
import com.recordingportal.backend.zoom.ZoomRecordingLookupService.RecordingFileCandidate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZoomRecordingMatcherTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate WED = LocalDate.of(2026, 11, 4);

    private final List<RecordingFileCandidate> zoom = List.of(
            recording("usual-11am", WED, 11, 1),
            recording("moved-noon", WED, 12, 2),
            recording("late-night", WED, 22, 5),
            recording("after-midnight", WED.plusDays(1), 0, 10),
            new RecordingFileCandidate("m", "chat", "chat-noon", "CHAT", at(WED, 12, 2), "u"));

    @Test
    void movedClassMatchesTheNewTimeOnly() {
        var matched = ZoomRecordingMatcher.filter(zoom, new ClassTime(WED, LocalTime.NOON, LocalTime.of(13, 0), Source.CHANGED), IST);
        assertEquals(List.of("moved-noon"), ids(matched));
    }

    @Test
    void usualTimeStillWorks() {
        var matched = ZoomRecordingMatcher.filter(zoom, new ClassTime(WED, LocalTime.of(11, 0), LocalTime.of(11, 45), Source.REGULAR), IST);
        assertEquals(List.of("usual-11am"), ids(matched));
    }

    @Test
    void lateNightClassAndTheEndToleranceDontWrapAround() {
        // 22:00-23:50: +15 min tolerance would wrap past midnight; must not match the 11am recording.
        var matched = ZoomRecordingMatcher.filter(zoom, new ClassTime(WED, LocalTime.of(22, 0), LocalTime.of(23, 50), Source.REGULAR), IST);
        assertEquals(List.of("late-night"), ids(matched));
        // Running past midnight: matches recordings that started that evening, not the next day's.
        var overnight = ZoomRecordingMatcher.filter(zoom, new ClassTime(WED, LocalTime.of(22, 0), LocalTime.of(0, 30), Source.REGULAR), IST);
        assertEquals(List.of("late-night"), ids(overnight));
    }

    @Test
    void zoomIsAskedForEveryUtcDateTheLocalDayCovers() {
        var range = ZoomRecordingLookupService.utcRangeFor(WED, IST);
        assertEquals(WED.minusDays(1), range.from()); // 00:00 IST = 18:30 UTC the day before
        assertEquals(WED, range.to());
        var utc = ZoomRecordingLookupService.utcRangeFor(WED, ZoneId.of("UTC"));
        assertEquals(WED, utc.from());
        assertEquals(WED, utc.to());
    }

    private static RecordingFileCandidate recording(String id, LocalDate date, int hour, int minute) {
        return new RecordingFileCandidate("meeting-" + id, "Class", id, "MP4", at(date, hour, minute), "https://zoom/" + id);
    }

    private static java.time.Instant at(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(IST).toInstant();
    }

    private static List<String> ids(List<RecordingFileCandidate> candidates) {
        return candidates.stream().map(RecordingFileCandidate::recordingFileId).toList();
    }
}
