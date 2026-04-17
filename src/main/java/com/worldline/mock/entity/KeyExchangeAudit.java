package com.worldline.mock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Audit log for every key exchange request (successful or failed). Tracks who
 * requested the public key, from where, and outcome.
 */
@Entity
@Table(name = "key_exchange_audit", indexes = {
    @Index(name = "idx_kea_requested_at", columnList = "requestedAt"),
    @Index(name = "idx_kea_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeyExchangeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * IP address of the requester
     */
    @Column(nullable = false, length = 45)
    private String requesterIp;

    /**
     * The endpoint that was called
     */
    @Column(nullable = false, length = 100)
    private String endpoint;

    /**
     * SUCCESS, UNAUTHORIZED, FORBIDDEN
     */
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String status;

    /**
     * RSA public key fingerprint that was served (null if failed)
     */
    @Column(length = 50)
    private String keyFingerprint;

    /**
     * Failure reason if not successful
     */
    @Column(length = 200)
    private String failureReason;

    /**
     * User-Agent header from request
     */
    @Column(length = 255)
    private String userAgent;

    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @PrePersist
    protected void onCreate() {
        this.requestedAt = LocalDateTime.now();
    }
}
