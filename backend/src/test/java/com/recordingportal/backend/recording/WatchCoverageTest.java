package com.recordingportal.backend.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class WatchCoverageTest {

    @Test
    void marksPlayedSegments() {
        String bits = WatchCoverage.merge(null, 100, List.of(new WatchCoverage.Range(0, 50)));
        assertEquals("1111100000", bits);
        assertEquals(50, WatchCoverage.percent(bits));
        assertEquals(50, WatchCoverage.watchedSeconds(bits, 100));
    }

    @Test
    void mergingIsIdempotentAndCumulative() {
        String once = WatchCoverage.merge(null, 100, List.of(new WatchCoverage.Range(0, 30)));
        String again = WatchCoverage.merge(once, 100, List.of(new WatchCoverage.Range(0, 30)));
        assertEquals(once, again);
        String more = WatchCoverage.merge(again, 100, List.of(new WatchCoverage.Range(80, 100)));
        assertEquals("1110000011", more);
    }

    @Test
    void seekingToTheEndDoesNotCountAsWatching() {
        // A scrub that only grazes a segment doesn't light it up.
        String bits = WatchCoverage.merge(null, 100, List.of(new WatchCoverage.Range(95, 99)));
        assertEquals(0, WatchCoverage.percent(bits));
    }

    @Test
    void ignoresGarbageRanges() {
        String bits = WatchCoverage.merge(null, 60, List.of(
                new WatchCoverage.Range(30, 10), new WatchCoverage.Range(Double.NaN, 5),
                new WatchCoverage.Range(-20, 10), new WatchCoverage.Range(55, 9999)));
        assertEquals("100001", bits);
    }

    @Test
    void handlesPartialLastSegment() {
        // 25s video: segments are 0-10, 10-20, 20-25.
        String bits = WatchCoverage.merge(null, 25, List.of(new WatchCoverage.Range(20, 25)));
        assertEquals("001", bits);
        assertEquals(25, WatchCoverage.watchedSeconds("111", 25));
    }
}
