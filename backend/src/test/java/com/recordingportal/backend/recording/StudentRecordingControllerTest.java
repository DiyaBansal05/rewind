package com.recordingportal.backend.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recordingportal.backend.batch.BatchRepository;
import com.recordingportal.backend.enrollment.Enrollment;
import com.recordingportal.backend.enrollment.EnrollmentRepository;
import com.recordingportal.backend.enrollment.EnrollmentStatus;
import com.recordingportal.backend.notification.NotificationService;
import com.recordingportal.backend.student.Student;
import com.recordingportal.backend.student.StudentRepository;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * A student can only request recordings for a batch they're an approved
 * member of. Deleting a batch (or removing the student from it) deletes the
 * enrollment, so this is also what stops requests after a batch is deleted.
 */
class StudentRecordingControllerTest {

    private final UUID studentId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();
    private final Principal principal = () -> studentId.toString();

    private final RecordingRequestRepository requests = mock(RecordingRequestRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final BatchRepository batches = mock(BatchRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final StudentRecordingController controller = new StudentRecordingController(
            requests, students, batches, enrollments, notifications,
            mock(AccessTokenService.class), mock(RecordingDecisionService.class));

    StudentRecordingControllerTest() {
        Student s = new Student();
        s.setId(studentId);
        when(students.findById(studentId)).thenReturn(Optional.of(s));
    }

    @Test
    void deletedBatchOrRemovedStudentCannotRequest() {
        when(enrollments.findByStudentIdAndBatchId(studentId, batchId)).thenReturn(Optional.empty());
        assertRefused();
    }

    @Test
    void pendingOrDeniedMembershipCannotRequest() {
        for (EnrollmentStatus status : new EnrollmentStatus[] {EnrollmentStatus.PENDING, EnrollmentStatus.DENIED}) {
            Enrollment e = new Enrollment();
            e.setStatus(status);
            when(enrollments.findByStudentIdAndBatchId(studentId, batchId)).thenReturn(Optional.of(e));
            assertRefused();
        }
    }

    private void assertRefused() {
        var response = controller.create(new StudentRecordingController.CreateRequest(batchId, LocalDate.of(2026, 10, 30)), principal);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(requests, never()).save(any());
        verify(notifications, never()).notifyAdminRequestRaised(any());
    }
}
