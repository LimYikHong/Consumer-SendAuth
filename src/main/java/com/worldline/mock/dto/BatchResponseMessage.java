package com.worldline.mock.dto;

import lombok.*;
import java.util.List;

/**
 * Kafka message published to batch-response topic. Field names match what the
 * producer's TransactionUpdateService expects.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchResponseMessage {

    /**
     * Same RtaBatch PK that was sent in the request
     */
    private Object batchId;

    /**
     * Merchant ID passed through from the request
     */
    private String merchantId;

    /**
     * "PROCESSED" on success, "FAILED" on error
     */
    private String batchStatus;

    /**
     * ISO timestamp of when processing completed
     */
    private String processedAt;

    /**
     * Human-readable error message (null if successful)
     */
    private String errorMessage;

    /**
     * One entry per transaction in the CSV
     */
    private List<TransactionResultDto> results;

    // Encrypted result CSV — producer decrypts with its RSA private key
    private String encryptedAesKey;
    private String encryptedContent;
    private String iv;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionResultDto {

        /**
         * rta_transaction.transaction_id (PK) — from CSV first column
         */
        private String transactionId;

        /**
         * "APPROVED" or "FAILED"
         */
        private String status;

        /**
         * "Authorized by mock service" or "Declined: insufficient funds (mock)"
         */
        private String remark;

        // Extra fields for internal audit
        private String merchantId;
        private String amountCents;
    }
}
