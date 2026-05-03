package com.worldline.mock.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Kafka message received from batch-request topic. Field names match the
 * producer's actual JSON payload.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchRequestMessage {

    /**
     * Unique batch identifier from the producer (may be numeric)
     */
    @JsonProperty("batchId")
    private String batchId;

    /**
     * Extra metadata from producer (optional)
     */
    private String merchantId;
    private String csvFilename;
    private Integer transactionCount;

    /**
     * RSA-OAEP encrypted AES session key (Base64). Producer field:
     * encryptedAesKeyBase64
     */
    @JsonProperty("encryptedAesKeyBase64")
    @JsonAlias("encryptedAesKey")
    private String encryptedAesKey;

    /**
     * AES-256 encrypted CSV content (Base64). Producer field:
     * encryptedFileBase64
     */
    @JsonProperty("encryptedFileBase64")
    @JsonAlias("encryptedCsvContent")
    private String encryptedCsvContent;

    /**
     * AES-CBC IV (Base64). Producer field: ivBase64
     */
    @JsonProperty("ivBase64")
    @JsonAlias("iv")
    private String iv;

    /**
     * Timestamp from the producer (optional)
     */
    private String timestamp;
}
