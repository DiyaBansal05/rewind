package com.recordingportal.backend.recording;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redemption already refuses expired links on its own (it checks
 * accessExpiresAt); this just keeps the stored status honest so the admin's
 * "Approved" tab and analytics don't show links that stopped working.
 */
@Component
public class RecordingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(RecordingExpiryJob.class);

    private final RecordingRequestRepository recordingRequestRepository;

    public RecordingExpiryJob(RecordingRequestRepository recordingRequestRepository) {
        this.recordingRequestRepository = recordingRequestRepository;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT30S")
    @Transactional
    public void expireOverdue() {
        int expired = recordingRequestRepository.expireOverdue(Instant.now());
        if (expired > 0) {
            log.info("Marked {} recording link(s) as expired", expired);
        }
    }
}
