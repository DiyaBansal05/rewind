package com.recordingportal.backend.notification;

import com.recordingportal.backend.enrollment.Enrollment;
import com.recordingportal.backend.recording.RecordingRequest;

public interface NotificationService {

    /** Pings the admin the moment a student raises a recording request. */
    void notifyAdminRequestRaised(RecordingRequest request);

    /** Lets a student know their requested recording is ready. */
    void notifyStudentRecordingApproved(RecordingRequest request, String accessUrl);

    /** Pings the admin the moment a student scans a QR code and asks to join a batch. */
    void notifyAdminEnrollmentRequested(Enrollment enrollment);

    /** Lets a student know they've been let into a batch they requested to join. */
    void notifyStudentEnrollmentApproved(Enrollment enrollment);
}
