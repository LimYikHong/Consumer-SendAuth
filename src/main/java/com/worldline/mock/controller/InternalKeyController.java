package com.worldline.mock.controller;

import com.worldline.mock.service.KeyExchangeAuditService;
import com.worldline.mock.service.KeyPairService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal API for key exchange — secured by API key + IP whitelist.
 *
 * The producer system calls this over HTTPS to retrieve the RSA public key,
 * which it then uses to encrypt a random AES session key for each batch.
 *
 * Endpoint: GET /api/internal/public-key Security: X-API-Key header + IP
 * whitelist Protocol: HTTPS only (port 8881)
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalKeyController {

    private final KeyPairService keyPairService;
    private final KeyExchangeAuditService auditService;

    /**
     * Returns the RSA public key in PEM format. The producer system should
     * cache this and re-fetch periodically or on failure.
     */
    @GetMapping(value = "/public-key", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getPublicKey(HttpServletRequest request) {
        log.info("📤 Public key requested from IP {}", getClientIp(request));
        String pem = keyPairService.getPublicKeyPem();
        String fingerprint = keyPairService.getPublicKeyFingerprint();

        auditService.logSuccess(
                getClientIp(request),
                "/api/internal/public-key",
                fingerprint,
                request.getHeader("User-Agent"));

        return ResponseEntity.ok(pem);
    }

    /**
     * Returns public key metadata (fingerprint, algorithm, format) for
     * verification.
     */
    @GetMapping(value = "/public-key/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> getPublicKeyInfo(HttpServletRequest request) {
        log.info("📤 Public key info requested from IP {}", getClientIp(request));

        auditService.logSuccess(
                getClientIp(request),
                "/api/internal/public-key/info",
                keyPairService.getPublicKeyFingerprint(),
                request.getHeader("User-Agent"));

        return ResponseEntity.ok(Map.of(
                "algorithm", "RSA",
                "keySize", "2048",
                "fingerprint", keyPairService.getPublicKeyFingerprint(),
                "format", "X.509/PEM"
        ));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
