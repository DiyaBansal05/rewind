package com.recordingportal.backend.batch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Works out when a batch actually had class on a given date: a single-date
 * change made by the admin wins; otherwise the batch's regular weekly slot
 * for that weekday applies; otherwise there was no class.
 *
 * Pure logic (no database), so the rules can be unit-tested directly.
 */
public final class ClassSchedule {

    private ClassSchedule() {
    }

    public enum Source { REGULAR, CHANGED, EXTRA }

    /**
     * When class ran on one date. {@code end} may be earlier than {@code start}
     * for a class that runs past midnight.
     */
    public record ClassTime(LocalDate date, LocalTime start, LocalTime end, Source source) {
        public boolean crossesMidnight() {
            return !end.isAfter(start);
        }
    }

    /** Why there's no class on a date -- used to explain a failed match to the admin. */
    public enum NoClassReason { CANCELLED, NOT_A_CLASS_DAY }

    public record Resolution(Optional<ClassTime> classTime, NoClassReason noClassReason) {
        static Resolution of(ClassTime t) {
            return new Resolution(Optional.of(t), null);
        }

        static Resolution none(NoClassReason reason) {
            return new Resolution(Optional.empty(), reason);
        }
    }

    public static Resolution resolve(Batch batch, LocalDate date, Optional<ClassChange> change) {
        if (change.isPresent()) {
            ClassChange c = change.get();
            if (c.isCancelled()) {
                return Resolution.none(NoClassReason.CANCELLED);
            }
            boolean regularDay = regularSlot(batch, date).isPresent();
            return Resolution.of(new ClassTime(date, c.getStartTime(), c.getEndTime(),
                    regularDay ? Source.CHANGED : Source.EXTRA));
        }
        return regularSlot(batch, date)
                .map(s -> Resolution.of(new ClassTime(date, s.getStartTime(), s.getEndTime(), Source.REGULAR)))
                .orElseGet(() -> Resolution.none(NoClassReason.NOT_A_CLASS_DAY));
    }

    /**
     * The most recent dates on which the batch had class (that have already
     * started by {@code now}), newest first -- what a student is offered when
     * requesting a recording. Cancelled classes are left out; extra classes
     * are included.
     */
    public static List<LocalDate> recentClassDates(
            Batch batch, Collection<ClassChange> changes, LocalDateTime now, int count, int maxDaysBack) {
        Map<LocalDate, ClassChange> byDate = changes.stream()
                .collect(Collectors.toMap(ClassChange::getClassDate, Function.identity(), (a, b) -> a));
        List<LocalDate> result = new ArrayList<>();
        LocalDate today = now.toLocalDate();
        for (int back = 0; back <= maxDaysBack && result.size() < count; back++) {
            LocalDate date = today.minusDays(back);
            Optional<ClassTime> time = resolve(batch, date, Optional.ofNullable(byDate.get(date))).classTime();
            if (time.isEmpty()) continue;
            if (back == 0 && now.toLocalTime().isBefore(time.get().start())) continue;
            result.add(date);
        }
        return result;
    }

    private static Optional<WeeklySlot> regularSlot(Batch batch, LocalDate date) {
        return batch.getWeeklySchedule().stream()
                .filter(s -> s.getDayOfWeek() == date.getDayOfWeek())
                .findFirst();
    }
}
