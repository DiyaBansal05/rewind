package com.recordingportal.backend.batch;

import com.recordingportal.backend.enrollment.Enrollment;
import com.recordingportal.backend.enrollment.EnrollmentRepository;
import com.recordingportal.backend.enrollment.EnrollmentStatus;
import com.recordingportal.backend.registration.QrTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/batches")
public class BatchController {

    private final BatchRepository batchRepository;
    private final QrTokenService qrTokenService;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassChangeRepository classChangeRepository;
    private final ZoneId zoneId;

    public BatchController(
            BatchRepository batchRepository,
            QrTokenService qrTokenService,
            EnrollmentRepository enrollmentRepository,
            ClassChangeRepository classChangeRepository,
            @Value("${app.institute.timezone}") String timezone) {
        this.batchRepository = batchRepository;
        this.qrTokenService = qrTokenService;
        this.enrollmentRepository = enrollmentRepository;
        this.classChangeRepository = classChangeRepository;
        this.zoneId = ZoneId.of(timezone);
    }

    /** One weekday's class time. endTime before startTime = runs past midnight. */
    public record SlotDto(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {
        static SlotDto from(WeeklySlot s) {
            return new SlotDto(s.getDayOfWeek(), s.getStartTime(), s.getEndTime());
        }
    }

    public record CreateBatchRequest(
            @NotBlank String name,
            @NotBlank String courseName,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotEmpty List<@Valid SlotDto> weeklySchedule) {
    }

    public record UpdateScheduleRequest(@NotEmpty List<@Valid SlotDto> weeklySchedule) {
    }

    public record BatchResponse(
            UUID id,
            String name,
            String courseName,
            LocalDate startDate,
            LocalDate endDate,
            List<SlotDto> weeklySchedule,
            BatchStatus status) {
        static BatchResponse from(Batch b) {
            return new BatchResponse(b.getId(), b.getName(), b.getCourseName(), b.getStartDate(), b.getEndDate(),
                    sortedSlots(b), b.getStatus());
        }
    }

    public record ClassChangeRequest(
            @NotNull LocalDate classDate, boolean cancelled, LocalTime startTime, LocalTime endTime, @Size(max = 255) String note) {
    }

    /** regularStart/regularEnd: the usual time that date, or null if it's not a regular class day. */
    public record ClassChangeResponse(
            UUID id, LocalDate classDate, boolean cancelled, LocalTime startTime, LocalTime endTime, String note,
            LocalTime regularStart, LocalTime regularEnd) {
        static ClassChangeResponse from(ClassChange c) {
            WeeklySlot regular = c.getBatch().getWeeklySchedule().stream()
                    .filter(s -> s.getDayOfWeek() == c.getClassDate().getDayOfWeek())
                    .findFirst().orElse(null);
            return new ClassChangeResponse(c.getId(), c.getClassDate(), c.isCancelled(), c.getStartTime(), c.getEndTime(),
                    c.getNote(), regular == null ? null : regular.getStartTime(), regular == null ? null : regular.getEndTime());
        }
    }

    public record RegistrationLinkResponse(String token, String registrationPath) {
    }

    public record EnrolledStudent(UUID enrollmentId, UUID studentId, String name, String phoneNumber) {
        static EnrolledStudent from(Enrollment e) {
            return new EnrolledStudent(e.getId(), e.getStudent().getId(), e.getStudent().getName(), e.getStudent().getPhoneNumber());
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateBatchRequest request) {
        String problem = validateSchedule(request.weeklySchedule());
        if (problem != null) {
            return ResponseEntity.badRequest().body(Map.of("message", problem));
        }
        Batch batch = new Batch();
        batch.setName(request.name());
        batch.setCourseName(request.courseName());
        batch.setStartDate(request.startDate());
        batch.setEndDate(request.endDate());
        batch.setWeeklySchedule(toSlots(request.weeklySchedule()));
        batchRepository.save(batch);
        return ResponseEntity.status(HttpStatus.CREATED).body(BatchResponse.from(batch));
    }

    /** Replaces the regular weekly timetable. Single-date changes are kept. */
    @PutMapping("/{id}/schedule")
    @Transactional
    public ResponseEntity<?> updateSchedule(@PathVariable UUID id, @Valid @RequestBody UpdateScheduleRequest request) {
        Batch batch = batchRepository.findById(id).orElse(null);
        if (batch == null) {
            return ResponseEntity.notFound().build();
        }
        String problem = validateSchedule(request.weeklySchedule());
        if (problem != null) {
            return ResponseEntity.badRequest().body(Map.of("message", problem));
        }
        batch.getWeeklySchedule().clear();
        batch.getWeeklySchedule().addAll(toSlots(request.weeklySchedule()));
        batchRepository.save(batch);
        return ResponseEntity.ok(BatchResponse.from(batch));
    }

    /**
     * Single-date class changes, for the admin only. By default: the last
     * 30 days and everything upcoming, so recent changes that still affect
     * pending requests stay visible.
     */
    @GetMapping("/{id}/class-changes")
    @Transactional(readOnly = true)
    public List<ClassChangeResponse> classChanges(@PathVariable UUID id) {
        LocalDate from = LocalDate.now(zoneId).minusDays(30);
        return classChangeRepository.findByBatchIdAndClassDateGreaterThanEqualOrderByClassDateAsc(id, from).stream()
                .map(ClassChangeResponse::from)
                .toList();
    }

    /** Creates or replaces the change for that date (one change per batch per date). */
    @PostMapping("/{id}/class-changes")
    @Transactional
    public ResponseEntity<?> saveClassChange(@PathVariable UUID id, @Valid @RequestBody ClassChangeRequest request) {
        Batch batch = batchRepository.findById(id).orElse(null);
        if (batch == null) {
            return ResponseEntity.notFound().build();
        }
        if (!request.cancelled()) {
            if (request.startTime() == null || request.endTime() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Give the new start and end time."));
            }
            if (request.startTime().equals(request.endTime())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Start and end time can't be the same."));
            }
        }
        ClassChange change = classChangeRepository.findByBatchIdAndClassDate(id, request.classDate())
                .orElseGet(ClassChange::new);
        change.setBatch(batch);
        change.setClassDate(request.classDate());
        change.setCancelled(request.cancelled());
        change.setStartTime(request.cancelled() ? null : request.startTime());
        change.setEndTime(request.cancelled() ? null : request.endTime());
        change.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        classChangeRepository.save(change);
        return ResponseEntity.ok(ClassChangeResponse.from(change));
    }

    @DeleteMapping("/{id}/class-changes/{changeId}")
    public ResponseEntity<Void> deleteClassChange(@PathVariable UUID id, @PathVariable UUID changeId) {
        return classChangeRepository.findById(changeId)
                .filter(c -> c.getBatch().getId().equals(id))
                .map(c -> {
                    classChangeRepository.delete(c);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** null if fine, otherwise a message the admin can act on. */
    static String validateSchedule(List<SlotDto> slots) {
        Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
        for (SlotDto s : slots) {
            if (!seen.add(s.dayOfWeek())) {
                return "Each day can only have one class time.";
            }
            if (s.startTime().equals(s.endTime())) {
                return "Start and end time can't be the same.";
            }
        }
        return null;
    }

    private static Set<WeeklySlot> toSlots(List<SlotDto> slots) {
        Set<WeeklySlot> result = new HashSet<>();
        slots.forEach(s -> result.add(new WeeklySlot(s.dayOfWeek(), s.startTime(), s.endTime())));
        return result;
    }

    static List<SlotDto> sortedSlots(Batch b) {
        return b.getWeeklySchedule().stream()
                .sorted(Comparator.comparing(WeeklySlot::getDayOfWeek))
                .map(SlotDto::from)
                .toList();
    }

    @GetMapping
    public List<BatchResponse> list() {
        return batchRepository.findAll().stream().map(BatchResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchResponse> get(@PathVariable UUID id) {
        return batchRepository.findById(id)
                .map(b -> ResponseEntity.ok(BatchResponse.from(b)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/registration-link")
    public ResponseEntity<RegistrationLinkResponse> registrationLink(@PathVariable UUID id) {
        if (!batchRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        String token = qrTokenService.issueToken(id);
        return ResponseEntity.ok(new RegistrationLinkResponse(token, "/register?token=" + token));
    }

    /** The approved roster for a batch -- used by the admin's "students in this batch"
     *  view, which also lets them remove a student (see AdminEnrollmentController). */
    @GetMapping("/{id}/students")
    public List<EnrolledStudent> students(@PathVariable UUID id) {
        return enrollmentRepository.findWithStudentByBatchIdAndStatus(id, EnrollmentStatus.APPROVED).stream()
                .map(EnrolledStudent::from)
                .toList();
    }
}
