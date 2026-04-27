package com.worldline.mock.service;

import com.worldline.mock.entity.ProducerRsaKey;
import com.worldline.mock.repository.ProducerRsaKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.security.MessageDigest;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/**
 * Fetches and manages the RSA public key from the producer system (RTA Bank). -
 * Calls GET https://localhost:8086/api/internal/public-key - Stores the PEM key
 * + metadata in DB - Enforces 30-day lifecycle with renewal window at day 25-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProducerKeyService {

    private final ProducerRsaKeyRepository keyRepository;
    private final KeyExchangeAuditService auditService;

    @Value("${app.producer.base-url:https://localhost:8086}")
    private String producerBaseUrl;

    @Value("${app.security.api-key}")
    private String apiKey;

    /**
     * Get the current active producer key (if any).
     */
    public Optional<ProducerRsaKey> getActiveKey() {
        return keyRepository.findTopByStatusOrderByFetchedAtDesc("ACTIVE");
    }

    /**
     * Get the latest key regardless of status.
     */
    public Optional<ProducerRsaKey> getLatestKey() {
        return keyRepository.findTopByOrderByFetchedAtDesc();
    }

    /**
     * Compute key status info for UI display.
     */
    public KeyStatus getKeyStatus() {
        Optional<ProducerRsaKey> latestOpt = getLatestKey();

        if (latestOpt.isEmpty()) {
            return new KeyStatus(null, false, true, false, false, 0, "NO_KEY",
                    "No producer RSA key — request one to enable encrypted communication.");
        }

        ProducerRsaKey key = latestOpt.get();
        LocalDateTime now = LocalDateTime.now();
        long daysSinceFetch = ChronoUnit.DAYS.between(key.getFetchedAt(), now);
        boolean isExpired = now.isAfter(key.getExpiresAt());
        boolean inRenewalWindow = daysSinceFetch >= 25 && !isExpired;
        boolean tooEarly = daysSinceFetch < 25 && !isExpired;

        // Auto-mark as expired if past 30 days
        if (isExpired && "ACTIVE".equals(key.getStatus())) {
            key.setStatus("EXPIRED");
            keyRepository.save(key);
        }

        String statusCode;
        String message;
        boolean buttonEnabled;

        if (isExpired) {
            statusCode = "EXPIRED";
            message = "Producer RSA key expired " + ChronoUnit.DAYS.between(key.getExpiresAt(), now) + " day(s) ago. Key is no longer valid.";
            buttonEnabled = false;
        } else if (inRenewalWindow) {
            statusCode = "RENEWAL_WINDOW";
            long daysLeft = ChronoUnit.DAYS.between(now, key.getExpiresAt());
            message = "Key expires in " + daysLeft + " day(s). Renew now to ensure uninterrupted service.";
            buttonEnabled = true;
        } else {
            statusCode = "ACTIVE";
            message = "Key is active. Renewal available from day 25 (in " + (25 - daysSinceFetch) + " days).";
            buttonEnabled = false;
        }

        return new KeyStatus(key, !isExpired, buttonEnabled, inRenewalWindow, isExpired,
                daysSinceFetch, statusCode, message);
    }

    /**
     * Fetch the RSA public key from the producer system via internal API.
     *
     * @param source who initiated the fetch (e.g. "auto-fetch",
     * "dashboard-user")
     */
    public ProducerRsaKey fetchProducerKey(String source) {
        log.info("🔑 Fetching RSA public key from producer at {}", producerBaseUrl);

        try {
            // Create SSL-trusting RestTemplate (producer uses self-signed cert)
            RestTemplate restTemplate = createTrustingRestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", apiKey);
            headers.setAccept(java.util.List.of(MediaType.TEXT_PLAIN));

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    producerBaseUrl + "/api/internal/public-key",
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new RuntimeException("Producer returned status " + response.getStatusCode());
            }

            String pem = response.getBody();
            String fingerprint = computeFingerprint(pem);

            // Mark any existing active keys as REPLACED
            getActiveKey().ifPresent(existing -> {
                existing.setStatus("REPLACED");
                keyRepository.save(existing);
                log.info("  Previous key [{}] marked as REPLACED", existing.getFingerprint());
            });

            // Save new key
            ProducerRsaKey newKey = ProducerRsaKey.builder()
                    .publicKeyPem(pem)
                    .fingerprint(fingerprint)
                    .fetchedAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .status("ACTIVE")
                    .requestedBy(source)
                    .build();
            keyRepository.save(newKey);

            // Audit log
            auditService.logSuccess("localhost", "/producer-key-fetch",
                    fingerprint, source);

            log.info("  ✅ Producer RSA key fetched and stored. Fingerprint: {}", fingerprint);
            return newKey;

        } catch (Exception e) {
            log.error("  ❌ Failed to fetch producer RSA key: {}", e.getMessage());
            auditService.logFailure("localhost", "/producer-key-fetch",
                    "FETCH_FAILED", e.getMessage(), source);
            throw new RuntimeException("Failed to fetch producer RSA key: " + e.getMessage(), e);
        }
    }

    /**
     * Compute SHA-256 fingerprint from PEM key string.
     */
    private String computeFingerprint(String pem) {
        try {
            String base64 = pem
                    .replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(keyBytes);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i > 0) {
                    sb.append(':');
                }
                sb.append(String.format("%02X", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * Create a RestTemplate that trusts all certificates (for self-signed
     * HTTPS).
     */
    private RestTemplate createTrustingRestTemplate() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new java.security.SecureRandom());

        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        return new RestTemplate();
    }

    // ---- Key status record for UI ----
    public record KeyStatus(
            ProducerRsaKey key,
            boolean hasValidKey,
            boolean buttonEnabled,
            boolean inRenewalWindow,
            boolean isExpired,
            long daysSinceFetch,
            String statusCode, // NO_KEY, ACTIVE, RENEWAL_WINDOW, EXPIRED
            String message
            ) {

        public String getButtonLabel() {
            if (key == null) {
                return "🔑 Request RSA Key";
            }
            return "🔄 Renew RSA Key";
        }
    }
}
