package com.recordingportal.backend.recording;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
