package com.recordingportal.backend.notification;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Completes the in-app notification channel: with real WhatsApp-to-student
 * delivery not landing until Phase 3, this is currently the only way a
 * student can retrieve messages sent via NotificationService (e.g. their
 * recording access link after approval).
 */
@RestController
@RequestMapping("/api/student/notifications")
public class StudentNotificationController {

    private final NotificationLogRepository notificationLogRepository;

    public StudentNotificationController(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    public record NotificationItem(UUID id, String message, Instant sentAt) {
    }

    @GetMapping
    public List<NotificationItem> myNotifications(Principal principal) {
        UUID studentId = UUID.fromString(principal.getName());
        return notificationLogRepository.findByRecipientTypeAndRecipientIdOrderBySentAtDesc(RecipientType.STUDENT, studentId).stream()
                .map(n -> new NotificationItem(n.getId(), n.getPayloadSummary(), n.getSentAt()))
                .toList();
    }
}
