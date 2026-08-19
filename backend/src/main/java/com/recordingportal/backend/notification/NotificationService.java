package com.recordingportal.backend.notification;

import com.recordingportal.backend.recording.RecordingRequest;

public interface NotificationService {

    /** Pings the admin the moment a student raises a recording request. */
    void notifyAdminRequestRaised(RecordingRequest request);

    /** Lets a student know their requested recording is ready. */
    void notifyStudentRecordingApproved(RecordingRequest request, String accessUrl);
}
