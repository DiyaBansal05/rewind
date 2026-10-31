package com.recordingportal.backend.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.recordingportal.backend.batch.ClassSchedule.NoClassReason;
import com.recordingportal.backend.batch.ClassSchedule.Source;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClassScheduleTest {

    // 2026-11-04 is a Wednesday.
    private static final LocalDate WED = LocalDate.of(2026, 11, 4);
    private static final LocalTime ELEVEN = LocalTime.of(11, 0);
    private static final LocalTime NOON = LocalTime.of(12, 0);

    /** Wed 11:00-12:00 and Sat/Sun 22:00-23:30; no class other days. */
    private final Batch batch = batch(Set.of(
            new WeeklySlot(DayOfWeek.WEDNESDAY, ELEVEN, NOON),
            new WeeklySlot(DayOfWeek.SATURDAY, LocalTime.of(22, 0), LocalTime.of(23, 30)),
            new WeeklySlot(DayOfWeek.SUNDAY, LocalTime.of(22, 0), LocalTime.of(23, 30))));

    @Test
    void differentDaysHaveTheirOwnTimes() {
        var wed = ClassSchedule.resolve(batch, WED, Optional.empty()).classTime().orElseThrow();
        assertEquals(ELEVEN, wed.start());
        assertEquals(Source.REGULAR, wed.source());
        var sat = ClassSchedule.resolve(batch, WED.plusDays(3), Optional.empty()).classTime().orElseThrow();
        assertEquals(LocalTime.of(22, 0), sat.start());
    }

    @Test
    void daysWithoutAClassHaveNone() {
        var thu = ClassSchedule.resolve(batch, WED.plusDays(1), Optional.empty());
        assertTrue(thu.classTime().isEmpty());
        assertEquals(NoClassReason.NOT_A_CLASS_DAY, thu.noClassReason());
    }

    @Test
    void aChangedDateUsesTheNewTime() {
        var moved = ClassSchedule.resolve(batch, WED, Optional.of(change(WED, false, NOON, LocalTime.of(13, 0))))
                .classTime().orElseThrow();
        assertEquals(NOON, moved.start());
        assertEquals(Source.CHANGED, moved.source());
    }

    @Test
    void extraClassOnANonClassDay() {
        LocalDate thu = WED.plusDays(1);
        var extra = ClassSchedule.resolve(batch, thu, Optional.of(change(thu, false, ELEVEN, NOON))).classTime().orElseThrow();
        assertEquals(Source.EXTRA, extra.source());
    }

    @Test
    void cancelledClassHasNoTime() {
        var r = ClassSchedule.resolve(batch, WED, Optional.of(change(WED, true, null, null)));
        assertTrue(r.classTime().isEmpty());
        assertEquals(NoClassReason.CANCELLED, r.noClassReason());
    }

    @Test
    void recentDatesApplyChangesAndSkipClassesNotStartedYet() {
        // Sunday 8 Nov at 21:00: Sunday's 22:00 class hasn't started yet.
        LocalDateTime now = LocalDateTime.of(2026, 11, 8, 21, 0);
        List<ClassChange> changes = List.of(
                change(WED, true, null, null),                          // Wed cancelled
                change(WED.plusDays(2), false, LocalTime.of(9, 0), NOON)); // extra class Fri
        List<LocalDate> dates = ClassSchedule.recentClassDates(batch, changes, now, 4, 21);
        assertEquals(List.of(
                LocalDate.of(2026, 11, 7),   // Sat
                LocalDate.of(2026, 11, 6),   // Fri (extra)
                LocalDate.of(2026, 11, 1),   // previous Sun
                LocalDate.of(2026, 10, 31)), // previous Sat
                dates);
    }

    @Test
    void lateNightClassCrossingMidnight() {
        var b = batch(Set.of(new WeeklySlot(DayOfWeek.SATURDAY, LocalTime.of(22, 0), LocalTime.of(0, 30))));
        var t = ClassSchedule.resolve(b, WED.plusDays(3), Optional.empty()).classTime().orElseThrow();
        assertTrue(t.crossesMidnight());
    }

    static Batch batch(Set<WeeklySlot> slots) {
        Batch b = new Batch();
        b.setName("Test batch");
        b.setStartDate(LocalDate.of(2026, 8, 1));
        b.setEndDate(LocalDate.of(2027, 1, 31));
        b.setWeeklySchedule(new java.util.HashSet<>(slots));
        return b;
    }

    private ClassChange change(LocalDate date, boolean cancelled, LocalTime start, LocalTime end) {
        ClassChange c = new ClassChange();
        c.setBatch(batch);
        c.setClassDate(date);
        c.setCancelled(cancelled);
        c.setStartTime(start);
        c.setEndTime(end);
        return c;
    }
}
