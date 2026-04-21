package com.worldline.mock.dto;

import lombok.*;
import java.util.List;

/**
 * Kafka message published to batch-response topic. Contains the authorization
 * results for each transaction in the batch.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchResponseMessage {

    private String batchId;
    private String status;           // COMPLETED or FAILED
    private int totalRecords;
    private int approvedCount;
    private int declinedCount;
    private String processedAt;
    private String errorMessage;     // null if successful
    private List<TransactionResultDto> results;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionResultDto {

        private String transactionId;
        private String merchantId;
        private String amountCents;
        private String authResult;    // APPROVED or DECLINED
        private String decisionReason;
    }
}
