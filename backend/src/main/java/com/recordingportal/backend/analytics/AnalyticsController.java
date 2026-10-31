package com.recordingportal.backend.analytics;

import com.recordingportal.backend.recording.RecordingRequestRepository;
import com.recordingportal.backend.recording.RecordingRequestStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsController {

    private static final int MAX_DAYS = 365;

    private final RecordingRequestRepository recordingRequestRepository;
    private final ZoneId zoneId;

    public AnalyticsController(
            RecordingRequestRepository recordingRequestRepository,
            @Value("${app.institute.timezone}") String timezone) {
        this.recordingRequestRepository = recordingRequestRepository;
        this.zoneId = ZoneId.of(timezone);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public AnalyticsCalculator.Report report(@RequestParam(defaultValue = "30") int days) {
        int window = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDate today = LocalDate.now(zoneId);
        LocalDate previousStart = today.minusDays(window * 2L - 1);
        var requests = recordingRequestRepository.findWithDetailsRequestedSince(previousStart.atStartOfDay(zoneId).toInstant());
        long pendingNow = recordingRequestRepository.countByStatus(RecordingRequestStatus.PENDING);
        return AnalyticsCalculator.compute(requests, window, today, zoneId, pendingNow);
    }
}
