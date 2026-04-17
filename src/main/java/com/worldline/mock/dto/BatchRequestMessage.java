package com.worldline.mock.dto;

import lombok.*;

/**
 * Kafka message received from batch-request topic. Contains the batch ID and
 * the encrypted CSV content (Base64-encoded).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchRequestMessage {

    /**
     * Unique batch identifier from the producer
     */
    private String batchId;

    /**
     * RSA-encrypted AES session key (Base64-encoded). Producer encrypts a
     * random AES-256 key with Consumer's RSA public key.
     */
    private String encryptedAesKey;

    /**
     * AES-encrypted CSV file content (Base64-encoded). Encrypted using the AES
     * session key above.
     */
    private String encryptedCsvContent;

    /**
     * AES IV for CBC mode (Base64-encoded, required)
     */
    private String iv;

    /**
     * Timestamp from the producer
     */
    private String timestamp;
}
