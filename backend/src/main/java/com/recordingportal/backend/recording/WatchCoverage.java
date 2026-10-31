package com.recordingportal.backend.recording;

import java.util.List;

/**
 * Tracks which parts of a recording have been played, as a string bitmap
 * with one character per SEGMENT_SECONDS slice ('1' = played at least once).
 *
 * The player sends the browser's own {@code video.played} ranges with each
 * progress ping. Those are cumulative for the page session, so merging them
 * here is idempotent -- a repeated or out-of-order ping can't inflate the
 * number, and seeking to the end without watching doesn't count as watching.
 */
public final class WatchCoverage {

    public static final int SEGMENT_SECONDS = 10;

    /** Upper bound so a bogus duration can't make us allocate a huge string (~14h). */
    static final int MAX_DURATION_SECONDS = 50_000;

    private WatchCoverage() {
    }

    public record Range(double start, double end) {
    }

    public static String merge(String existing, int durationSeconds, List<Range> played) {
        int segments = segmentCount(durationSeconds);
        char[] bits = new char[segments];
        for (int i = 0; i < segments; i++) {
            bits[i] = existing != null && i < existing.length() && existing.charAt(i) == '1' ? '1' : '0';
        }
        if (played != null) {
            for (Range r : played) {
                if (r == null || !(r.end() > r.start()) || Double.isNaN(r.start())) continue;
                double start = Math.max(0, r.start());
                double end = Math.min(durationSeconds, r.end());
                // A segment counts once most of it was played, so a short
                // scrub across a boundary doesn't light up two segments.
                for (int i = (int) (start / SEGMENT_SECONDS); i < segments; i++) {
                    double segStart = (double) i * SEGMENT_SECONDS;
                    double segEnd = Math.min(durationSeconds, segStart + SEGMENT_SECONDS);
                    if (segStart >= end) break;
                    double overlap = Math.min(end, segEnd) - Math.max(start, segStart);
                    if (overlap >= (segEnd - segStart) / 2) bits[i] = '1';
                }
            }
        }
        return new String(bits);
    }

    public static int watchedSeconds(String bits, Integer durationSeconds) {
        if (bits == null || durationSeconds == null) return 0;
        int ones = 0;
        for (int i = 0; i < bits.length(); i++) if (bits.charAt(i) == '1') ones++;
        return Math.min(durationSeconds, ones * SEGMENT_SECONDS);
    }

    /** 0-100, or 0 if nothing is known yet. */
    public static int percent(String bits) {
        if (bits == null || bits.isEmpty()) return 0;
        int ones = 0;
        for (int i = 0; i < bits.length(); i++) if (bits.charAt(i) == '1') ones++;
        return (int) Math.round(ones * 100.0 / bits.length());
    }

    static int segmentCount(int durationSeconds) {
        int d = Math.max(1, Math.min(durationSeconds, MAX_DURATION_SECONDS));
        return (d + SEGMENT_SECONDS - 1) / SEGMENT_SECONDS;
    }
}
