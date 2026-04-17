package com.worldline.mock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Individual transaction record from the CSV. Stores the original data +
 * authorization decision. Optimized for bulk insert via Hibernate batching.
 */
@Entity
@Table(name = "transaction_record", indexes = {
    @Index(name = "idx_txn_batch_id", columnList = "batchId"),
    @Index(name = "idx_txn_account_number", columnList = "accountNumber"),
    @Index(name = "idx_txn_result", columnList = "authResult"),
    @Index(name = "idx_txn_batch_result", columnList = "batchId, authResult")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Links back to the batch this record belongs to
     */
    @Column(nullable = false, length = 64)
    private String batchId;

    /**
     * Original transaction ID from producer
     */
    @Column(nullable = false, length = 64)
    private String transactionId;

    @Column(nullable = false, length = 32)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private AccountStatus accountStatus;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(length = 100)
    private String merchantName;

    @Column(length = 20)
    private String merchantCategory;

    /**
     * The authorization decision made by this mock service
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private AuthorizationResult authResult;

    /**
     * Reason for the decision
     */
    @Column(length = 200)
    private String decisionReason;

    @Column(updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        this.processedAt = LocalDateTime.now();
    }
}
