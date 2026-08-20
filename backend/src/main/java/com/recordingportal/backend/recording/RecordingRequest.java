package com.recordingportal.backend.recording;

import com.recordingportal.backend.admin.Admin;
import com.recordingportal.backend.batch.Batch;
import com.recordingportal.backend.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "recording_request")
@Getter
@Setter
@NoArgsConstructor
public class RecordingRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(nullable = false)
    private LocalDate classDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordingRequestStatus status = RecordingRequestStatus.PENDING;

    @Column(nullable = false)
    private Instant requestedAt = Instant.now();

    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_admin_id")
    private Admin decidedByAdmin;

    private String accessTokenHash;

    private Instant accessExpiresAt;

    private String zoomRecordingFileId;

    private String zoomMeetingUuid;
}
