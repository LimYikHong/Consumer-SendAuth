package com.worldline.mock.service;

import com.opencsv.bean.CsvToBeanBuilder;
import com.worldline.mock.dto.BatchRequestMessage;
import com.worldline.mock.dto.BatchResponseMessage;
import com.worldline.mock.dto.BatchResponseMessage.TransactionResultDto;
import com.worldline.mock.dto.TransactionCsvRow;
import com.worldline.mock.entity.*;
import com.worldline.mock.repository.BatchJobRepository;
import com.worldline.mock.repository.TransactionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates Kafka-based batch processing pipeline: 1. Decrypt CSV 2. Parse
 * rows 3. Evaluate via AuthorizationEngine 4. Persist results 5. Build response
 * message
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchProcessingService {

    private final CsvDecryptionService csvDecryptionService;
    private final CsvEncryptionService csvEncryptionService;
    private final ProducerKeyService producerKeyService;
    private final AuthorizationEngine authorizationEngine;
    private final BatchJobRepository batchJobRepository;
    private final TransactionRecordRepository transactionRecordRepository;

    @Value("${app.processing.batch-chunk-size:500}")
    private int chunkSize;

    @Transactional
    public BatchResponseMessage process(BatchRequestMessage request) {
        String batchId = request.getBatchId();
        log.info("▶ Processing batch [{}]", batchId);

        // Idempotency
        if (batchJobRepository.existsByBatchId(batchId)) {
            log.warn("Batch [{}] already processed, skipping", batchId);
            BatchJob existing = batchJobRepository.findByBatchId(batchId).orElseThrow();
            List<TransactionRecord> existingRecords = transactionRecordRepository.findByBatchId(batchId);
            String existingResultCsv = buildResultCsv(existingRecords);
            CsvEncryptionService.EncryptedPayload existingEncrypted = encryptForProducer(existingResultCsv);
            return buildResponse(existing, existingRecords, existingEncrypted);
        }

        BatchJob job = BatchJob.builder()
                .batchId(batchId)
                .status(BatchStatus.RECEIVED)
                .build();
        batchJobRepository.save(job);

        try {
            job.setStatus(BatchStatus.PROCESSING);
            batchJobRepository.save(job);

            String csvContent = csvDecryptionService.decrypt(
                    request.getEncryptedAesKey(),
                    request.getEncryptedCsvContent(),
                    request.getIv());

            List<TransactionCsvRow> rows = parseCsv(csvContent);
            log.info("  Parsed {} transaction rows for batch [{}]", rows.size(), batchId);

            List<TransactionRecord> records = new ArrayList<>(rows.size());
            int approved = 0, declined = 0;

            for (TransactionCsvRow row : rows) {
                long amountCents = 0L;
                if (row.getAmountCents() != null && !row.getAmountCents().isBlank()) {
                    amountCents = Long.parseLong(row.getAmountCents().trim());
                }

                AuthorizationEngine.Decision decision = authorizationEngine.evaluate(amountCents);

                TransactionRecord record = TransactionRecord.builder()
                        .batchId(batchId)
                        .transactionId(row.getTransactionId())
                        .merchantId(row.getMerchantId())
                        .merchantCustomer(row.getMerchantCustomer())
                        .maskedPan(row.getMaskedPan())
                        .amountCents(amountCents)
                        .currency(row.getCurrency())
                        .actualBillingDate(row.getActualBillingDate())
                        .recurringReference(row.getRecurringReference())
                        .authResult(decision.result())
                        .decisionReason(decision.reason())
                        .build();
                records.add(record);

                if (decision.result() == AuthorizationResult.APPROVED) {
                    approved++;
                } else {
                    declined++;
                }
            }

            saveInChunks(records);

            job.setTotalRecords(rows.size());
            job.setApprovedCount(approved);
            job.setDeclinedCount(declined);
            job.setStatus(BatchStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            batchJobRepository.save(job);

            log.info("  ✅ Batch [{}] completed: {} approved, {} declined out of {}",
                    batchId, approved, declined, rows.size());

            // Re-encrypt result CSV with producer's RSA public key
            String resultCsv = buildResultCsv(records);
            CsvEncryptionService.EncryptedPayload encrypted = encryptForProducer(resultCsv);

            return buildResponse(job, records, encrypted);

        } catch (Exception e) {
            log.error("  ❌ Batch [{}] failed: {}", batchId, e.getMessage(), e);
            job.setStatus(BatchStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            batchJobRepository.save(job);

            return BatchResponseMessage.builder()
                    .batchId(batchId).batchStatus("FAILED")
                    .errorMessage(e.getMessage())
                    .processedAt(LocalDateTime.now().toString())
                    .build();
        }
    }

    private List<TransactionCsvRow> parseCsv(String csvContent) {
        try (StringReader reader = new StringReader(csvContent)) {
            return new CsvToBeanBuilder<TransactionCsvRow>(reader)
                    .withType(TransactionCsvRow.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withIgnoreEmptyLine(true)
                    .build()
                    .parse();
        }
    }

    private void saveInChunks(List<TransactionRecord> records) {
        for (int i = 0; i < records.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, records.size());
            transactionRecordRepository.saveAll(records.subList(i, end));
            transactionRecordRepository.flush();
        }
    }

    /**
     * Encrypt result CSV with producer's RSA public key (auto-fetch if needed).
     */
    private CsvEncryptionService.EncryptedPayload encryptForProducer(String csv) {
        java.security.PublicKey producerKey = null;
        try {
            var active = producerKeyService.getActiveKey();
            if (active.isPresent()) {
                producerKey = csvEncryptionService.parsePublicKeyPem(active.get().getPublicKeyPem());
                log.info("  Using stored producer RSA key for result encryption");
            } else {
                log.info("  🔑 No stored producer key — auto-fetching...");
                var fetched = producerKeyService.fetchProducerKey("auto-fetch");
                producerKey = csvEncryptionService.parsePublicKeyPem(fetched.getPublicKeyPem());
            }
        } catch (Exception e) {
            log.warn("  ⚠ Could not get producer RSA key ({}), encrypting with own key", e.getMessage());
        }
        try {
            return csvEncryptionService.encrypt(csv, producerKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt result CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Build result CSV: original columns + auth_result + decision_reason.
     */
    private String buildResultCsv(List<TransactionRecord> records) {
        StringWriter writer = new StringWriter();
        writer.write("transaction_id,merchant_id,merchant_customer,masked_pan,amount_cents,currency,actual_billing_date,recurring_reference,auth_result,decision_reason\n");
        for (TransactionRecord r : records) {
            writer.write(String.join(",",
                    nvl(r.getTransactionId()), nvl(r.getMerchantId()), nvl(r.getMerchantCustomer()),
                    nvl(r.getMaskedPan()), String.valueOf(r.getAmountCents()), nvl(r.getCurrency()),
                    nvl(r.getActualBillingDate()), nvl(r.getRecurringReference()),
                    r.getAuthResult().name(), nvl(r.getDecisionReason())
            ));
            writer.write("\n");
        }
        return writer.toString();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private BatchResponseMessage buildResponse(BatchJob job, List<TransactionRecord> records,
            CsvEncryptionService.EncryptedPayload encrypted) {
        List<TransactionResultDto> resultDtos = records.stream()
                .map(r -> {
                    boolean approved = r.getAuthResult() == AuthorizationResult.APPROVED;
                    return TransactionResultDto.builder()
                            .transactionId(r.getTransactionId())
                            .status(approved ? "APPROVED" : "DECLINED")
                            .remark(approved ? "Authorized by mock service" : "Declined: insufficient funds")
                            .merchantId(r.getMerchantId())
                            .amountCents(String.valueOf(r.getAmountCents()))
                            .build();
                })
                .toList();

        return BatchResponseMessage.builder()
                .batchId(job.getBatchId())
                .batchStatus("PROCESSED")
                .processedAt(LocalDateTime.now().toString())
                .results(resultDtos)
                // Note: encrypted CSV is stored in MinIO — not sent over Kafka to avoid 1MB limit
                .build();
    }
}
