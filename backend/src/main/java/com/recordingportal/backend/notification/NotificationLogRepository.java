package com.recordingportal.backend.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    List<NotificationLog> findByRecipientTypeAndRecipientIdOrderBySentAtDesc(RecipientType recipientType, UUID recipientId);
}
