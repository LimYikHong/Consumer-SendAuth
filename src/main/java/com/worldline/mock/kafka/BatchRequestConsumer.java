package com.worldline.mock.kafka;

import com.worldline.mock.dto.BatchRequestMessage;
import com.worldline.mock.dto.BatchResponseMessage;
import com.worldline.mock.service.BatchProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer: listens on `batch-request` topic, processes the encrypted
 * CSV, and publishes the authorization results to `batch-response` topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchRequestConsumer {

    private final BatchProcessingService batchProcessingService;
    private final KafkaTemplate<String, BatchResponseMessage> kafkaTemplate;

    @Value("${app.kafka.topic.batch-response}")
    private String batchResponseTopic;

    @KafkaListener(
            topics = "${app.kafka.topic.batch-request}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onBatchRequest(BatchRequestMessage message) {
        log.info("📥 Received batch request [{}]", message.getBatchId());

        try {
            // Process the batch (decrypt → parse → authorize → persist)
            BatchResponseMessage response = batchProcessingService.process(message);

            // Publish results back to batch-response topic
            kafkaTemplate.send(batchResponseTopic, message.getBatchId(), response)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("❌ Failed to send batch response [{}]: {}",
                                    message.getBatchId(), ex.getMessage());
                        } else {
                            log.info("📤 Batch response [{}] sent to topic '{}' partition {} offset {}",
                                    message.getBatchId(),
                                    batchResponseTopic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });

        } catch (Exception e) {
            log.error("❌ Unhandled error processing batch [{}]: {}",
                    message.getBatchId(), e.getMessage(), e);

            // Still publish a FAILED response so the producer knows
            BatchResponseMessage errorResponse = BatchResponseMessage.builder()
                    .batchId(message.getBatchId())
                    .status("FAILED")
                    .errorMessage("Internal processing error: " + e.getMessage())
                    .build();

            kafkaTemplate.send(batchResponseTopic, message.getBatchId(), errorResponse);
        }
    }
}
