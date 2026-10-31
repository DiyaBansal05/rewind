package com.recordingportal.backend.recording;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccessEventRepository extends JpaRepository<AccessEvent, UUID> {

    List<AccessEvent> findByRecordingRequestIdOrderByAccessedAtDesc(UUID recordingRequestId);

    /** [recordingRequestId, successful opens] for each request that was opened at least once. */
    @Query("select e.recordingRequest.id, count(e) from AccessEvent e "
            + "where e.success = true and e.recordingRequest.id in :ids group by e.recordingRequest.id")
    List<Object[]> countSuccessfulOpens(Collection<UUID> ids);
}
