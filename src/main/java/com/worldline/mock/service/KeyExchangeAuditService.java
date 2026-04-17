package com.worldline.mock.service;

import com.worldline.mock.entity.KeyExchangeAudit;
import com.worldline.mock.repository.KeyExchangeAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Logs every key exchange attempt to the database for audit/tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeyExchangeAuditService {

    private final KeyExchangeAuditRepository auditRepository;

    public void logSuccess(String ip, String endpoint, String fingerprint, String userAgent) {
        KeyExchangeAudit audit = KeyExchangeAudit.builder()
                .requesterIp(ip)
                .endpoint(endpoint)
                .status("SUCCESS")
                .keyFingerprint(fingerprint)
                .userAgent(userAgent)
                .build();
        auditRepository.save(audit);
        log.info("🔑 Key exchange SUCCESS from IP {} — fingerprint {}", ip, fingerprint);
    }

    public void logFailure(String ip, String endpoint, String status, String reason, String userAgent) {
        KeyExchangeAudit audit = KeyExchangeAudit.builder()
                .requesterIp(ip)
                .endpoint(endpoint)
                .status(status)
                .failureReason(reason)
                .userAgent(userAgent)
                .build();
        auditRepository.save(audit);
        log.warn("🔑 Key exchange {} from IP {} — {}", status, ip, reason);
    }
}
