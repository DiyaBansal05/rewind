package com.recordingportal.backend.batch;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassChangeRepository extends JpaRepository<ClassChange, UUID> {

    Optional<ClassChange> findByBatchIdAndClassDate(UUID batchId, LocalDate classDate);

    List<ClassChange> findByBatchIdAndClassDateBetweenOrderByClassDateAsc(UUID batchId, LocalDate from, LocalDate to);

    List<ClassChange> findByBatchIdAndClassDateGreaterThanEqualOrderByClassDateAsc(UUID batchId, LocalDate from);
}
