package com.recordingportal.backend.notification;

import com.recordingportal.backend.admin.AdminRepository;
import com.recordingportal.backend.enrollment.Enrollment;
import com.recordingportal.backend.recording.RecordingRequest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final WhatsAppCloudApiClient whatsAppCloudApiClient;
    private final NotificationLogRepository notificationLogRepository;
    private final AdminRepository adminRepository;
    private final String adminRecipientNumber;
    private final ZoneId zoneId;

    public NotificationServiceImpl(
            WhatsAppCloudApiClient whatsAppCloudApiClient,
            NotificationLogRepository notificationLogRepository,
            AdminRepository adminRepository,
            @Value("${app.whatsapp.admin-recipient-number}") String adminRecipientNumber,
            @Value("${app.institute.timezone}") String timezone) {
        this.whatsAppCloudApiClient = whatsAppCloudApiClient;
        this.notificationLogRepository = notificationLogRepository;
        this.adminRepository = adminRepository;
        this.adminRecipientNumber = adminRecipientNumber;
        this.zoneId = ZoneId.of(timezone);
    }

    @Override
    public void notifyAdminRequestRaised(RecordingRequest request) {
        String message = "New recording request: %s requested %s (%s) for %s.".formatted(
                request.getStudent().getName(),
                request.getBatch().getName(),
                request.getBatch().getCourseName(),
                request.getClassDate());
        notifyAdmin(message);
    }

    @Override
    public void notifyStudentRecordingApproved(RecordingRequest request, String accessUrl) {
        String message = "Your recording for %s on %s is ready: %s".formatted(
                request.getBatch().getName(), request.getClassDate(), accessUrl);
        notifyStudentInApp(request.getStudent().getId(), message);
    }

    @Override
    public void notifyStudentAccessExtended(RecordingRequest request, String accessUrl) {
        String until = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
                .format(request.getAccessExpiresAt().atZone(zoneId));
        String message = "You have more time to watch %s (class on %s). Access now ends %s.".formatted(
                request.getBatch().getName(), request.getClassDate(), until);
        if (accessUrl != null) {
            message += " Watch: " + accessUrl;
        }
        notifyStudentInApp(request.getStudent().getId(), message);
    }

    private void notifyStudentInApp(UUID studentId, String message) {
        NotificationLog logEntry = new NotificationLog();
        logEntry.setRecipientType(RecipientType.STUDENT);
        logEntry.setRecipientId(studentId);
        logEntry.setChannel(NotificationChannel.IN_APP);
        logEntry.setPayloadSummary(message);
        logEntry.setDeliveryStatus(DeliveryStatus.SENT);
        notificationLogRepository.save(logEntry);
    }

    @Override
    public void notifyAdminEnrollmentRequested(Enrollment enrollment) {
        String message = "New join request: %s wants to join %s (%s).".formatted(
                enrollment.getStudent().getName(),
                enrollment.getBatch().getName(),
                enrollment.getBatch().getCourseName());
        notifyAdmin(message);
    }

    @Override
    public void notifyStudentEnrollmentApproved(Enrollment enrollment) {
        String message = "You're in! Your request to join %s (%s) has been approved.".formatted(
                enrollment.getBatch().getName(), enrollment.getBatch().getCourseName());
        notifyStudentInApp(enrollment.getStudent().getId(), message);
    }

    private void notifyAdmin(String message) {
        DeliveryStatus deliveryStatus;
        if (!whatsAppCloudApiClient.isConfigured()) {
            deliveryStatus = DeliveryStatus.SKIPPED;
        } else {
            deliveryStatus = whatsAppCloudApiClient.sendText(adminRecipientNumber, message)
                    ? DeliveryStatus.SENT
                    : DeliveryStatus.FAILED;
        }

        UUID adminId = adminRepository.findAll().stream().findFirst().map(a -> a.getId()).orElse(null);

        NotificationLog logEntry = new NotificationLog();
        logEntry.setRecipientType(RecipientType.ADMIN);
        logEntry.setRecipientId(adminId);
        logEntry.setChannel(NotificationChannel.WHATSAPP);
        logEntry.setPayloadSummary(message);
        logEntry.setDeliveryStatus(deliveryStatus);
        notificationLogRepository.save(logEntry);
    }
}
