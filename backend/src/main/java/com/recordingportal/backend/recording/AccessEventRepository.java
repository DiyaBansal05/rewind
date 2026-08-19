package com.recordingportal.backend.recording;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessEventRepository extends JpaRepository<AccessEvent, UUID> {
}
