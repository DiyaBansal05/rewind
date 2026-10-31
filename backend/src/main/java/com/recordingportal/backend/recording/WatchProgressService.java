package com.recordingportal.backend.recording;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates recording links and records what happens with them: each opening
 * of the player page (an AccessEvent) and the progress pings it sends while
 * the video plays.
 */
@Service
public class WatchProgressService {

    private final AccessTokenService accessTokenService;
    private final RecordingRequestRepository recordingRequestRepository;
    private final AccessEventRepository accessEventRepository;

    public WatchProgressService(
            AccessTokenService accessTokenService,
            RecordingRequestRepository recordingRequestRepository,
            AccessEventRepository accessEventRepository) {
        this.accessTokenService = accessTokenService;
        this.recordingRequestRepository = recordingRequestRepository;
        this.accessEventRepository = accessEventRepository;
    }

    public enum LinkState { UNKNOWN, INACTIVE, VALID }

    public record Resolved(LinkState state, RecordingRequest request) {
    }

    public record ProgressPing(UUID sessionId, Double position, Double duration, List<List<Double>> played) {
    }

    /** UNKNOWN: not one of our tokens. INACTIVE: real link, but revoked/expired/replaced. */
    @Transactional(readOnly = true)
    public Resolved resolve(String token) {
        Optional<UUID> id = accessTokenService.parseRecordingRequestId(token);
        if (id.isEmpty()) {
            return new Resolved(LinkState.UNKNOWN, null);
        }
        RecordingRequest request = recordingRequestRepository.findWithDetailsById(id.get()).orElse(null);
        if (request == null) {
            return new Resolved(LinkState.UNKNOWN, null);
        }
        boolean valid = request.getStatus() == RecordingRequestStatus.APPROVED
                && accessTokenService.hash(token).equals(request.getAccessTokenHash())
                && request.getAccessExpiresAt() != null
                && request.getAccessExpiresAt().isAfter(Instant.now());
        return new Resolved(valid ? LinkState.VALID : LinkState.INACTIVE, request);
    }

    /** Logs one opening of the player page; the returned id ties later progress pings to it. */
    @Transactional
    public AccessEvent recordOpen(RecordingRequest request, boolean success, String userAgent) {
        AccessEvent event = new AccessEvent();
        event.setRecordingRequest(request);
        event.setSuccess(success);
        if (userAgent != null) {
            event.setUserAgent(userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent);
        }
        return accessEventRepository.save(event);
    }

    /** Returns false if the link is no longer valid, so the player can stop and say so. */
    @Transactional
    public boolean recordProgress(String token, ProgressPing ping) {
        Resolved resolved = resolve(token);
        if (resolved.state() != LinkState.VALID) {
            return false;
        }
        // Same transaction as resolve(), so this is the managed entity.
        RecordingRequest request = resolved.request();
        Instant now = Instant.now();

        int duration = clampSeconds(ping.duration());
        if (duration > 0) {
            request.setDurationSeconds(duration);
        }
        Integer knownDuration = request.getDurationSeconds();
        if (knownDuration != null && knownDuration > 0) {
            List<WatchCoverage.Range> ranges = ping.played() == null ? List.of() : ping.played().stream()
                    .filter(r -> r != null && r.size() == 2 && r.get(0) != null && r.get(1) != null)
                    .limit(500)
                    .map(r -> new WatchCoverage.Range(r.get(0), r.get(1)))
                    .toList();
            request.setWatchedSegments(WatchCoverage.merge(request.getWatchedSegments(), knownDuration, ranges));
        }
        int position = clampSeconds(ping.position());
        request.setLastPositionSeconds(position);
        request.setLastWatchedAt(now);

        if (ping.sessionId() != null) {
            accessEventRepository.findById(ping.sessionId())
                    .filter(e -> e.getRecordingRequest().getId().equals(request.getId()))
                    .ifPresent(e -> {
                        int previous = e.getMaxPositionSeconds() == null ? 0 : e.getMaxPositionSeconds();
                        e.setMaxPositionSeconds(Math.max(previous, position));
                        e.setLastProgressAt(now);
                    });
        }
        return true;
    }

    private static int clampSeconds(Double value) {
        if (value == null || value.isNaN() || value.isInfinite() || value < 0) return 0;
        return (int) Math.min(value, WatchCoverage.MAX_DURATION_SECONDS);
    }
}
