package com.recordingportal.backend.zoom;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Exchanges the Server-to-Server OAuth app's credentials for a bearer token
 * (grant_type=account_credentials) and caches it in memory for reuse, since
 * Zoom access tokens are short-lived (~1hr) and re-fetching per request would
 * be wasteful and eventually rate-limited.
 *
 * Single-JVM in-memory cache is intentional here -- this app runs as one
 * instance, so there's no need for a distributed cache (Redis etc.) yet.
 */
@Service
public class ZoomOAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(ZoomOAuthTokenService.class);

    /** Refresh this many seconds before actual expiry, to avoid using a token that expires mid-request. */
    private static final long REFRESH_BUFFER_SECONDS = 60;

    private final String accountId;
    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiresAt = Instant.EPOCH;

    public ZoomOAuthTokenService(
            @Value("${app.zoom.account-id}") String accountId,
            @Value("${app.zoom.client-id}") String clientId,
            @Value("${app.zoom.client-secret}") String clientSecret,
            @Value("${app.zoom.oauth-base-url}") String oauthBaseUrl) {
        this.accountId = accountId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = RestClient.builder().baseUrl(oauthBaseUrl).build();
    }

    public boolean isConfigured() {
        return !accountId.isBlank() && !clientId.isBlank() && !clientSecret.isBlank();
    }

    /**
     * Returns a valid access token, fetching a fresh one if the cached token
     * is missing or close to expiry. Throws if Zoom credentials aren't
     * configured at all -- callers should check isConfigured() first if they
     * want to fail gracefully instead.
     */
    public String getAccessToken() {
        if (!isConfigured()) {
            throw new IllegalStateException("Zoom credentials are not configured");
        }
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiresAt.minusSeconds(REFRESH_BUFFER_SECONDS))) {
            return cachedToken;
        }
        lock.lock();
        try {
            // Re-check after acquiring the lock in case another thread just refreshed it.
            if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiresAt.minusSeconds(REFRESH_BUFFER_SECONDS))) {
                return cachedToken;
            }
            return fetchAndCacheToken();
        } finally {
            lock.unlock();
        }
    }

    private String fetchAndCacheToken() {
        String basicAuth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
        log.info("Fetching a fresh Zoom access token");

        TokenResponse response = restClient.post()
                .uri("/oauth/token?grant_type=account_credentials&account_id={accountId}", accountId)
                .header("Authorization", "Basic " + basicAuth)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("Zoom did not return an access token");
        }

        cachedToken = response.accessToken();
        cachedTokenExpiresAt = Instant.now().plusSeconds(response.expiresIn());
        return cachedToken;
    }

    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType,
            String scope) {
    }
}
