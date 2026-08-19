package com.recordingportal.backend.notification;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over Meta's WhatsApp Business Cloud API. Only used for the
 * single admin-facing "a recording request came in" ping (see
 * NotificationServiceImpl) -- that has exactly one fixed recipient, so it
 * works on Meta's free test-number tier without business verification.
 * No-ops (with a log line) when not configured, so the app runs fine
 * without live WhatsApp credentials during local development.
 */
@Component
public class WhatsAppCloudApiClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudApiClient.class);

    private final boolean enabled;
    private final String phoneNumberId;
    private final String accessToken;
    private final RestClient restClient;

    public WhatsAppCloudApiClient(
            @Value("${app.whatsapp.enabled}") boolean enabled,
            @Value("${app.whatsapp.api-base-url}") String apiBaseUrl,
            @Value("${app.whatsapp.phone-number-id}") String phoneNumberId,
            @Value("${app.whatsapp.access-token}") String accessToken) {
        this.enabled = enabled;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).build();
    }

    public boolean isConfigured() {
        return enabled && !phoneNumberId.isBlank() && !accessToken.isBlank();
    }

    public boolean sendText(String toNumber, String body) {
        if (!isConfigured() || toNumber.isBlank()) {
            log.warn("WhatsApp not configured; skipping send. Would have sent to {}: {}", toNumber, body);
            return false;
        }
        try {
            restClient.post()
                    .uri("/{phoneNumberId}/messages", phoneNumberId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "messaging_product", "whatsapp",
                            "to", toNumber,
                            "type", "text",
                            "text", Map.of("body", body)))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}", toNumber, e);
            return false;
        }
    }
}
