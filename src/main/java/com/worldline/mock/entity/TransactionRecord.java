package com.worldline.mock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Individual transaction record from the CSV. Stores the original data +
 * authorization decision. Fields match the producer's CSV columns:
 * transaction_id, merchant_id, merchant_customer, masked_pan, amount_cents,
 * currency, actual_billing_date, recurring_reference
 */
@Entity
@Table(name = "transaction_record", indexes = {
    @Index(name = "idx_txn_batch_id", columnList = "batchId"),
    @Index(name = "idx_txn_merchant_id", columnList = "merchantId"),
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
     * Original transaction ID from producer (their DB primary key)
     */
    @Column(nullable = false, length = 64)
    private String transactionId;

    /**
     * Merchant identifier
     */
    @Column(nullable = false, length = 64)
    private String merchantId;

    /**
     * Merchant's customer reference
     */
    @Column(length = 100)
    private String merchantCustomer;

    /**
     * Masked card PAN
     */
    @Column(length = 32)
    private String maskedPan;

    /**
     * Amount in cents (e.g. 50000 = $500.00)
     */
    @Column(nullable = false)
    private Long amountCents;

    @Column(length = 3)
    private String currency;

    /**
     * Billing date from the producer
     */
    @Column(length = 30)
    private String actualBillingDate;

    /**
     * Recurring reference if applicable
     */
    @Column(length = 100)
    private String recurringReference;

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
