package com.recordingportal.backend.recording;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecordingRequestRepository extends JpaRepository<RecordingRequest, UUID> {

    @Query("select r from RecordingRequest r join fetch r.batch join fetch r.student "
            + "where r.status = :status order by r.requestedAt asc")
    List<RecordingRequest> findByStatusOrderByRequestedAtAsc(RecordingRequestStatus status);

    @Query("select r from RecordingRequest r join fetch r.batch "
            + "where r.student.id = :studentId order by r.requestedAt desc")
    List<RecordingRequest> findByStudentIdOrderByRequestedAtDesc(UUID studentId);

    Optional<RecordingRequest> findByAccessTokenHash(String accessTokenHash);

    @Query("select r from RecordingRequest r join fetch r.batch join fetch r.student where r.id = :id")
    Optional<RecordingRequest> findWithDetailsById(UUID id);

    long countByStudentId(UUID studentId);

    long countByStatus(RecordingRequestStatus status);

    @Query("select r from RecordingRequest r join fetch r.batch join fetch r.student order by r.requestedAt desc")
    List<RecordingRequest> findAllWithDetails();

    @Query("select r from RecordingRequest r join fetch r.batch join fetch r.student "
            + "where r.requestedAt >= :since order by r.requestedAt asc")
    List<RecordingRequest> findWithDetailsRequestedSince(Instant since);

    @Query("select r.status, count(r) from RecordingRequest r group by r.status")
    List<Object[]> countGroupedByStatus();

    /** Flips approved links whose time is up to EXPIRED, so lists and filters show the real state. */
    @Modifying
    @Query("update RecordingRequest r set r.status = com.recordingportal.backend.recording.RecordingRequestStatus.EXPIRED "
            + "where r.status = com.recordingportal.backend.recording.RecordingRequestStatus.APPROVED "
            + "and r.accessExpiresAt < :now")
    int expireOverdue(Instant now);
}
