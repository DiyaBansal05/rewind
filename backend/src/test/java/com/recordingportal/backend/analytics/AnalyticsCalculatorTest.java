package com.recordingportal.backend.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.recordingportal.backend.batch.Batch;
import com.recordingportal.backend.recording.RecordingRequest;
import com.recordingportal.backend.recording.RecordingRequestStatus;
import com.recordingportal.backend.student.Student;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticsCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    private final Batch python = batch("Python Morning A");
    private final Batch tally = batch("Tally Evening");
    private final Student ananya = student("Ananya");
    private final Student karthik = student("Karthik");

    @Test
    void summarisesTheWindowAndComparesWithThePreviousOne() {
        List<RecordingRequest> requests = List.of(
                // previous 7-day window
                request(ananya, python, TODAY.minusDays(10), RecordingRequestStatus.DENIED, 60, null),
                // current window
                request(ananya, python, TODAY.minusDays(2), RecordingRequestStatus.APPROVED, 30, "1100"),
                request(ananya, tally, TODAY.minusDays(1), RecordingRequestStatus.EXPIRED, 90, null),
                request(karthik, python, TODAY, RecordingRequestStatus.DENIED, 120, null),
                request(karthik, python, TODAY, RecordingRequestStatus.PENDING, 0, null));

        var report = AnalyticsCalculator.compute(requests, 7, TODAY, ZONE, 1);

        assertEquals(4, report.totalRequests());
        assertEquals(1, report.previousPeriodRequests());
        assertEquals(67, report.approvalRatePercent()); // 2 granted of 3 decided
        assertEquals(90L, report.medianDecisionMinutes()); // 30, 90, 120
        assertEquals(2, report.grantedCount());
        assertEquals(1, report.watchedCount());
        assertEquals(50, report.watchedRatePercent());
        assertEquals(50, report.averageWatchPercent());

        assertEquals(7, report.perDay().size());
        assertEquals(2, report.perDay().get(6).count());
        assertEquals("Python Morning A", report.perBatch().get(0).batchName());
        assertEquals(3, report.perBatch().get(0).count());
        assertEquals("Ananya", report.topRequesters().get(0).name());
        assertEquals(List.of("Python Morning A", "Tally Evening"), report.topRequesters().get(0).batchNames());
        assertEquals(7, report.missedWeekdays().size());
        assertEquals(DayOfWeek.MONDAY, report.missedWeekdays().get(0).day());
    }

    @Test
    void emptyWindowHasNoRatesRatherThanZero() {
        var report = AnalyticsCalculator.compute(List.of(), 30, TODAY, ZONE, 0);
        assertEquals(0, report.totalRequests());
        assertNull(report.approvalRatePercent());
        assertNull(report.medianDecisionMinutes());
        assertNull(report.watchedRatePercent());
        assertEquals(30, report.perDay().size());
    }

    private static Batch batch(String name) {
        Batch b = new Batch();
        b.setId(UUID.randomUUID());
        b.setName(name);
        return b;
    }

    private static Student student(String name) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setName(name);
        s.setPhoneNumber("98450" + Math.abs(name.hashCode() % 100000));
        return s;
    }

    private static RecordingRequest request(Student s, Batch b, LocalDate day, RecordingRequestStatus status,
                                            long decisionMinutes, String watched) {
        RecordingRequest r = new RecordingRequest();
        r.setId(UUID.randomUUID());
        r.setStudent(s);
        r.setBatch(b);
        r.setClassDate(day.minusDays(1));
        r.setStatus(status);
        Instant requestedAt = day.atTime(10, 0).atZone(ZONE).toInstant();
        r.setRequestedAt(requestedAt);
        if (status != RecordingRequestStatus.PENDING) r.setDecidedAt(requestedAt.plus(Duration.ofMinutes(decisionMinutes)));
        r.setWatchedSegments(watched);
        return r;
    }
}
