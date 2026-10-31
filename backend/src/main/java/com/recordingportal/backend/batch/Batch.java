package com.recordingportal.backend.batch;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "batch")
@Getter
@Setter
@NoArgsConstructor
public class Batch {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String courseName;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private String zoomMeetingId;

    /** The regular weekly timetable, at most one class per weekday. Single-date
     *  changes live in ClassChange; see ClassSchedule for how they combine. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "batch_weekly_slot", joinColumns = @JoinColumn(name = "batch_id"))
    private Set<WeeklySlot> weeklySchedule = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status = BatchStatus.ACTIVE;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
