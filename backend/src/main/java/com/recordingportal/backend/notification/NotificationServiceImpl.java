package com.recordingportal.backend.notification;

import com.recordingportal.backend.admin.AdminRepository;
import com.recordingportal.backend.recording.RecordingRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final WhatsAppCloudApiClient whatsAppCloudApiClient;
    private final NotificationLogRepository notificationLogRepository;
    private final AdminRepository adminRepository;
    private final String adminRecipientNumber;

    public NotificationServiceImpl(
            WhatsAppCloudApiClient whatsAppCloudApiClient,
            NotificationLogRepository notificationLogRepository,
            AdminRepository adminRepository,
            @Value("${app.whatsapp.admin-recipient-number}") String adminRecipientNumber) {
        this.whatsAppCloudApiClient = whatsAppCloudApiClient;
        this.notificationLogRepository = notificationLogRepository;
        this.adminRepository = adminRepository;
        this.adminRecipientNumber = adminRecipientNumber;
    }

    @Override
    public void notifyAdminRequestRaised(RecordingRequest request) {
        String message = "New recording request: %s requested %s (%s) for %s.".formatted(
                request.getStudent().getName(),
                request.getBatch().getName(),
                request.getBatch().getCourseName(),
                request.getClassDate());

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

    @Override
    public void notifyStudentRecordingApproved(RecordingRequest request, String accessUrl) {
        String message = "Your recording for %s on %s is ready: %s".formatted(
                request.getBatch().getName(), request.getClassDate(), accessUrl);

        NotificationLog logEntry = new NotificationLog();
        logEntry.setRecipientType(RecipientType.STUDENT);
        logEntry.setRecipientId(request.getStudent().getId());
        logEntry.setChannel(NotificationChannel.IN_APP);
        logEntry.setPayloadSummary(message);
        logEntry.setDeliveryStatus(DeliveryStatus.SENT);
        notificationLogRepository.save(logEntry);
    }
}
