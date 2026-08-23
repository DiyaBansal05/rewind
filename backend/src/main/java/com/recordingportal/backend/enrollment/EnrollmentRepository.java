package com.recordingportal.backend.enrollment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    boolean existsByStudentIdAndBatchId(UUID studentId, UUID batchId);

    boolean existsByStudentIdAndBatchIdAndStatus(UUID studentId, UUID batchId, EnrollmentStatus status);

    List<Enrollment> findByStudentId(UUID studentId);

    Optional<Enrollment> findByStudentIdAndBatchId(UUID studentId, UUID batchId);

    @Query("select e from Enrollment e join fetch e.batch where e.student.id = :studentId")
    List<Enrollment> findWithBatchByStudentId(UUID studentId);

    @Query("select e from Enrollment e join fetch e.batch join fetch e.student "
            + "where e.status = :status order by e.enrolledAt asc")
    List<Enrollment> findWithDetailsByStatusOrderByEnrolledAtAsc(EnrollmentStatus status);

    @Query("select e from Enrollment e join fetch e.student "
            + "where e.batch.id = :batchId and e.status = :status order by e.student.name asc")
    List<Enrollment> findWithStudentByBatchIdAndStatus(UUID batchId, EnrollmentStatus status);

    @Query("select e from Enrollment e join fetch e.batch join fetch e.student where e.id = :id")
    Optional<Enrollment> findWithDetailsById(UUID id);
}
