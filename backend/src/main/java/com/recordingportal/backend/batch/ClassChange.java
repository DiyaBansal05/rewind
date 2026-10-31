package com.recordingportal.backend.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An admin's change to a single class date: moved to another time,
 * cancelled, or an extra class on a day the batch doesn't normally meet.
 * Only the admin sees these; students just request the date as usual and
 * the recording is matched against the changed time.
 */
@Entity
@Table(name = "batch_class_change")
@Getter
@Setter
@NoArgsConstructor
public class ClassChange {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(nullable = false)
    private LocalDate classDate;

    @Column(nullable = false)
    private boolean cancelled;

    private LocalTime startTime;

    private LocalTime endTime;

    private String note;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
