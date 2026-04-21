package com.worldline.mock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Stores the RSA public key fetched from the producer system (RTA Bank at port
 * 8086). Tracks key lifecycle: fetched date, expiry (30 days), and renewal
 * window (day 25-30).
 */
@Entity
@Table(name = "producer_rsa_key")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProducerRsaKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * PEM-encoded RSA public key
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyPem;

    /**
     * SHA-256 fingerprint (first 8 bytes, colon-separated hex)
     */
    @Column(nullable = false, length = 50)
    private String fingerprint;

    /**
     * When the key was fetched
     */
    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    /**
     * When the key expires (fetchedAt + 30 days)
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Status: ACTIVE, EXPIRED, REPLACED
     */
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String status;

    /**
     * Who triggered the fetch (e.g. "dashboard-user")
     */
    @Column(length = 100)
    private String requestedBy;

    @PrePersist
    protected void onCreate() {
        if (this.fetchedAt == null) {
            this.fetchedAt = LocalDateTime.now();
        }
        if (this.expiresAt == null) {
            this.expiresAt = this.fetchedAt.plusDays(30);
        }
        if (this.status == null) {
            this.status = "ACTIVE";
        }
    }
}
