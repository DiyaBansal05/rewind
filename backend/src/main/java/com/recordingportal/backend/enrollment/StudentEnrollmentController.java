package com.recordingportal.backend.enrollment;

import com.recordingportal.backend.batch.ClassChangeRepository;
import com.recordingportal.backend.batch.ClassSchedule;
import com.recordingportal.backend.batch.WeeklySlot;
import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/batches")
public class StudentEnrollmentController {

    /** How many recent class dates to offer, and how far back to look for them. */
    private static final int RECENT_CLASS_COUNT = 8;
    private static final int RECENT_DAYS_BACK = 21;

    private final EnrollmentRepository enrollmentRepository;
    private final ClassChangeRepository classChangeRepository;
    private final ZoneId zoneId;

    public StudentEnrollmentController(
            EnrollmentRepository enrollmentRepository,
            ClassChangeRepository classChangeRepository,
            @Value("${app.institute.timezone}") String timezone) {
        this.enrollmentRepository = enrollmentRepository;
        this.classChangeRepository = classChangeRepository;
        this.zoneId = ZoneId.of(timezone);
    }

    /**
     * recentClassDates lets the request form offer only dates the batch
     * actually had class, with the admin's one-off changes already applied
     * (moved and extra classes in, cancelled ones out). Class times are
     * deliberately not sent: students just pick the date they missed.
     */
    public record EnrolledBatch(
            UUID batchId, String batchName, String courseName, EnrollmentStatus status,
            List<DayOfWeek> classDays, List<LocalDate> recentClassDates) {
    }

    /** Returns both APPROVED batches and still-PENDING join requests (but not DENIED ones)
     *  so the dashboard can show a "waiting for approval" state instead of just silently
     *  looking empty while a request is under review. */
    @GetMapping
    public List<EnrolledBatch> myBatches(Principal principal) {
        UUID studentId = UUID.fromString(principal.getName());
        LocalDateTime now = LocalDateTime.now(zoneId);
        return enrollmentRepository.findWithBatchByStudentId(studentId).stream()
                .filter(e -> e.getStatus() != EnrollmentStatus.DENIED)
                .map(e -> toEnrolledBatch(e, now))
                .toList();
    }

    private EnrolledBatch toEnrolledBatch(Enrollment e, LocalDateTime now) {
        var b = e.getBatch();
        List<LocalDate> recent = List.of();
        if (e.getStatus() == EnrollmentStatus.APPROVED) {
            LocalDate today = now.toLocalDate();
            var changes = classChangeRepository.findByBatchIdAndClassDateBetweenOrderByClassDateAsc(
                    b.getId(), today.minusDays(RECENT_DAYS_BACK), today);
            recent = ClassSchedule.recentClassDates(b, changes, now, RECENT_CLASS_COUNT, RECENT_DAYS_BACK);
        }
        List<DayOfWeek> days = b.getWeeklySchedule().stream().map(WeeklySlot::getDayOfWeek).sorted().toList();
        return new EnrolledBatch(b.getId(), b.getName(), b.getCourseName(), e.getStatus(), days, recent);
    }
}
