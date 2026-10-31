package com.recordingportal.backend.analytics;

import com.recordingportal.backend.recording.RecordingRequest;
import com.recordingportal.backend.recording.RecordingRequestStatus;
import com.recordingportal.backend.recording.WatchCoverage;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure computation over a list of requests -- no database access -- so the
 * numbers on the admin's analytics page can be unit-tested directly.
 */
public final class AnalyticsCalculator {

    private static final Set<RecordingRequestStatus> GRANTED =
            Set.of(RecordingRequestStatus.APPROVED, RecordingRequestStatus.REVOKED, RecordingRequestStatus.EXPIRED);
    private static final int TOP_REQUESTERS = 5;

    private AnalyticsCalculator() {
    }

    public record DayCount(LocalDate date, long count) {
    }

    public record BatchCount(UUID batchId, String batchName, long count) {
    }

    public record Requester(UUID studentId, String name, String phoneNumber, List<String> batchNames, long count) {
    }

    public record WeekdayCount(DayOfWeek day, long count) {
    }

    public record Report(
            int days, LocalDate from, LocalDate to,
            long totalRequests, long previousPeriodRequests,
            Integer approvalRatePercent, Long medianDecisionMinutes,
            long grantedCount, long watchedCount, Integer watchedRatePercent, Integer averageWatchPercent,
            long pendingNow,
            List<DayCount> perDay, List<BatchCount> perBatch, List<Requester> topRequesters,
            List<WeekdayCount> missedWeekdays) {
    }

    /**
     * @param requests every request made since the start of the *previous*
     *                 period (twice the window), so the change can be shown.
     */
    public static Report compute(List<RecordingRequest> requests, int days, LocalDate today, ZoneId zone, long pendingNow) {
        LocalDate from = today.minusDays(days - 1L);
        Instant periodStart = from.atStartOfDay(zone).toInstant();
        Instant previousStart = from.minusDays(days).atStartOfDay(zone).toInstant();

        List<RecordingRequest> current = requests.stream()
                .filter(r -> !r.getRequestedAt().isBefore(periodStart))
                .toList();
        long previous = requests.stream()
                .filter(r -> !r.getRequestedAt().isBefore(previousStart) && r.getRequestedAt().isBefore(periodStart))
                .count();

        List<RecordingRequest> decided = current.stream()
                .filter(r -> r.getStatus() != RecordingRequestStatus.PENDING)
                .toList();
        List<RecordingRequest> granted = current.stream().filter(r -> GRANTED.contains(r.getStatus())).toList();
        List<RecordingRequest> watched = granted.stream()
                .filter(r -> WatchCoverage.percent(r.getWatchedSegments()) > 0)
                .toList();

        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) byDay.put(d, 0L);
        for (RecordingRequest r : current) {
            byDay.computeIfPresent(r.getRequestedAt().atZone(zone).toLocalDate(), (k, v) -> v + 1);
        }

        Map<DayOfWeek, Long> byWeekday = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) byWeekday.put(d, 0L);
        for (RecordingRequest r : current) byWeekday.merge(r.getClassDate().getDayOfWeek(), 1L, Long::sum);

        return new Report(
                days, from, today,
                current.size(), previous,
                percent(granted.size(), decided.size()),
                medianMinutes(decided),
                granted.size(), watched.size(),
                percent(watched.size(), granted.size()),
                watched.isEmpty() ? null : (int) Math.round(watched.stream()
                        .mapToInt(r -> WatchCoverage.percent(r.getWatchedSegments())).average().orElse(0)),
                pendingNow,
                byDay.entrySet().stream().map(e -> new DayCount(e.getKey(), e.getValue())).toList(),
                perBatch(current),
                topRequesters(current),
                byWeekday.entrySet().stream().map(e -> new WeekdayCount(e.getKey(), e.getValue())).toList());
    }

    private static Integer percent(long part, long whole) {
        return whole == 0 ? null : (int) Math.round(part * 100.0 / whole);
    }

    private static Long medianMinutes(List<RecordingRequest> decided) {
        List<Long> minutes = decided.stream()
                .filter(r -> r.getDecidedAt() != null)
                .map(r -> Duration.between(r.getRequestedAt(), r.getDecidedAt()).toMinutes())
                .sorted()
                .toList();
        if (minutes.isEmpty()) return null;
        int mid = minutes.size() / 2;
        return minutes.size() % 2 == 1 ? minutes.get(mid) : (minutes.get(mid - 1) + minutes.get(mid)) / 2;
    }

    private static List<BatchCount> perBatch(List<RecordingRequest> current) {
        Map<UUID, List<RecordingRequest>> grouped = current.stream()
                .collect(Collectors.groupingBy(r -> r.getBatch().getId(), LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream()
                .map(rs -> new BatchCount(rs.get(0).getBatch().getId(), rs.get(0).getBatch().getName(), rs.size()))
                .sorted(Comparator.comparingLong(BatchCount::count).reversed().thenComparing(BatchCount::batchName))
                .toList();
    }

    private static List<Requester> topRequesters(List<RecordingRequest> current) {
        Map<UUID, List<RecordingRequest>> grouped = current.stream()
                .collect(Collectors.groupingBy(r -> r.getStudent().getId(), LinkedHashMap::new, Collectors.toList()));
        List<Requester> all = new ArrayList<>();
        grouped.forEach((id, rs) -> {
            var student = rs.get(0).getStudent();
            List<String> batches = new ArrayList<>(rs.stream()
                    .map(r -> r.getBatch().getName())
                    .collect(Collectors.toCollection(TreeSet::new)));
            all.add(new Requester(id, student.getName(), student.getPhoneNumber(), batches, rs.size()));
        });
        return all.stream()
                .sorted(Comparator.comparingLong(Requester::count).reversed().thenComparing(Requester::name))
                .limit(TOP_REQUESTERS)
                .toList();
    }
}
