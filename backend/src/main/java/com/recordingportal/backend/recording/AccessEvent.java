package com.recordingportal.backend.recording;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "access_event")
@Getter
@Setter
@NoArgsConstructor
public class AccessEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recording_request_id", nullable = false)
    private RecordingRequest recordingRequest;

    @Column(nullable = false)
    private Instant accessedAt = Instant.now();

    @Column(nullable = false)
    private boolean success;
}
