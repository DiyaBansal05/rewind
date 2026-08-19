package com.recordingportal.backend.enrollment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    boolean existsByStudentIdAndBatchId(UUID studentId, UUID batchId);

    List<Enrollment> findByStudentId(UUID studentId);

    Optional<Enrollment> findByStudentIdAndBatchId(UUID studentId, UUID batchId);

    @Query("select e from Enrollment e join fetch e.batch where e.student.id = :studentId")
    List<Enrollment> findWithBatchByStudentId(UUID studentId);
}
