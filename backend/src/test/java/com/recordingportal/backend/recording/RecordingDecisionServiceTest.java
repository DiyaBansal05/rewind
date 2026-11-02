package com.recordingportal.backend.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recordingportal.backend.admin.AdminRepository;
import com.recordingportal.backend.batch.Batch;
import com.recordingportal.backend.enrollment.Enrollment;
import com.recordingportal.backend.enrollment.EnrollmentRepository;
import com.recordingportal.backend.enrollment.EnrollmentStatus;
import com.recordingportal.backend.notification.NotificationService;
import com.recordingportal.backend.recording.RecordingDecisionService.Outcome;
import com.recordingportal.backend.student.Student;
import com.recordingportal.backend.zoom.ZoomRecordingMatcher;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordingDecisionServiceTest {

    private final RecordingRequestRepository requests = mock(RecordingRequestRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final ZoomRecordingMatcher matcher = mock(ZoomRecordingMatcher.class);
    private final RecordingDecisionService service = new RecordingDecisionService(
            requests, mock(AdminRepository.class), new AccessTokenService("x".repeat(48), 48),
            mock(NotificationService.class), matcher, enrollments, "http://localhost");

    private final RecordingRequest request = pendingRequest();

    @Test
    void refusesToApproveWhileTheJoinRequestIsPending() {
        enrolledAs(EnrollmentStatus.PENDING);
        var result = service.approve(request.getId(), null, UUID.randomUUID(), null);
        assertEquals(Outcome.NOT_ENROLLED, result.outcome());
        assertTrue(result.message().contains("hasn't been approved yet"));
        verify(matcher, never()).match(any(), any());
        verify(requests, never()).save(any());
    }

    @Test
    void refusesWhenTheStudentWasRemovedOrDenied() {
        enrolledAs(null);
        assertEquals(Outcome.NOT_ENROLLED, service.approve(request.getId(), null, UUID.randomUUID(), null).outcome());
        enrolledAs(EnrollmentStatus.DENIED);
        assertEquals(Outcome.NOT_ENROLLED, service.approve(request.getId(), null, UUID.randomUUID(), null).outcome());
    }

    @Test
    void checkingTheRecordingIsRefusedToo() {
        enrolledAs(EnrollmentStatus.PENDING);
        var check = service.check(request.getId());
        assertEquals(Outcome.NOT_ENROLLED, check.problem());
        verify(matcher, never()).match(any(), any());
    }

    private void enrolledAs(EnrollmentStatus status) {
        Optional<Enrollment> e = Optional.empty();
        if (status != null) {
            Enrollment enrollment = new Enrollment();
            enrollment.setStatus(status);
            e = Optional.of(enrollment);
        }
        when(enrollments.findByStudentIdAndBatchId(request.getStudent().getId(), request.getBatch().getId())).thenReturn(e);
    }

    private RecordingRequest pendingRequest() {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setName("Test Student");
        Batch b = new Batch();
        b.setId(UUID.randomUUID());
        b.setName("Spoken English");
        RecordingRequest r = new RecordingRequest();
        r.setId(UUID.randomUUID());
        r.setStudent(s);
        r.setBatch(b);
        r.setClassDate(LocalDate.of(2026, 10, 28));
        when(requests.findWithDetailsById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }
}
