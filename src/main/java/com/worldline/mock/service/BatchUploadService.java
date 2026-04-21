package com.worldline.mock.service;

import com.opencsv.bean.CsvToBeanBuilder;
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
 * Handles the POST /api/internal/batch-upload flow: 1. Decrypt the uploaded
 * encrypted CSV (RSA+AES hybrid) 2. Store incoming file in MinIO 3. Parse CSV,
 * apply authorization rules 4. Build result CSV with authResult +
 * decisionReason columns appended 5. Re-encrypt result CSV (new AES key + RSA)
 * 6. Store result file in MinIO 7. Return encrypted result payload
 *
 * Producer CSV columns:
 * transaction_id,merchant_id,merchant_customer,masked_pan,amount_cents,currency,actual_billing_date,recurring_reference
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchUploadService {

    private final CsvDecryptionService csvDecryptionService;
    private final CsvEncryptionService csvEncryptionService;
    private final AuthorizationEngine authorizationEngine;
    private final BatchJobRepository batchJobRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final MinioService minioService;

    @Value("${app.processing.batch-chunk-size:500}")
    private int chunkSize;

    @Transactional
    public BatchUploadResult process(String batchId, String encryptedAesKey,
            String encryptedContent, String iv,
            String producerPublicKeyPem) {
        log.info("▶ Processing batch upload [{}]", batchId);

        // Idempotency check
        if (batchJobRepository.existsByBatchId(batchId)) {
            log.warn("Batch [{}] already processed, skipping", batchId);
            throw new IllegalStateException("Batch " + batchId + " already processed");
        }

        // Create batch job
        BatchJob job = BatchJob.builder()
                .batchId(batchId)
                .status(BatchStatus.RECEIVED)
                .build();
        batchJobRepository.save(job);

        try {
            // Store incoming encrypted file in MinIO
            minioService.storeIncomingFile(batchId, encryptedContent.getBytes());

            // Decrypt
            job.setStatus(BatchStatus.PROCESSING);
            batchJobRepository.save(job);

            String csvContent = csvDecryptionService.decrypt(encryptedAesKey, encryptedContent, iv);

            // DEBUG: log first 500 chars to verify columns
            log.info("  Decrypted CSV preview (first 500 chars): {}",
                    csvContent.substring(0, Math.min(500, csvContent.length())));

            // Store decrypted CSV in MinIO for audit
            minioService.storePlaintextCsv(batchId, csvContent);

            // Parse CSV
            List<TransactionCsvRow> rows = parseCsv(csvContent);
            log.info("  Parsed {} rows for batch [{}]", rows.size(), batchId);

            // Process each row
            List<TransactionRecord> records = new ArrayList<>(rows.size());
            List<ResultCsvRow> resultRows = new ArrayList<>(rows.size());
            int approved = 0, declined = 0;

            for (TransactionCsvRow row : rows) {
                // Debug: log first row
                if (records.isEmpty()) {
                    log.info("  First row: txnId={}, merchantId={}, customer={}, pan={}, amountCents={}, currency={}",
                            row.getTransactionId(), row.getMerchantId(), row.getMerchantCustomer(),
                            row.getMaskedPan(), row.getAmountCents(), row.getCurrency());
                }

                // Parse amount_cents (Long, in cents)
                long amountCents = 0L;
                if (row.getAmountCents() != null && !row.getAmountCents().isBlank()) {
                    amountCents = Long.parseLong(row.getAmountCents().trim());
                } else {
                    log.warn("  Row {} has null/empty amount_cents, defaulting to 0", row.getTransactionId());
                }

                // Evaluate authorization
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

                // Build result CSV row (original + auth result)
                resultRows.add(new ResultCsvRow(
                        row.getTransactionId(),
                        row.getMerchantId(),
                        row.getMerchantCustomer(),
                        row.getMaskedPan(),
                        row.getAmountCents(),
                        row.getCurrency(),
                        row.getActualBillingDate(),
                        row.getRecurringReference(),
                        decision.result().name(),
                        decision.reason()
                ));

                if (decision.result() == AuthorizationResult.APPROVED) {
                    approved++; 
                }else {
                    declined++;
                }
            }

            // Bulk save
            saveInChunks(records);

            // Update batch job
            job.setTotalRecords(rows.size());
            job.setApprovedCount(approved);
            job.setDeclinedCount(declined);
            job.setStatus(BatchStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            batchJobRepository.save(job);

            // Build result CSV
            String resultCsv = buildResultCsv(resultRows);

            // Encrypt result CSV
            java.security.PublicKey recipientKey = null;
            if (producerPublicKeyPem != null && !producerPublicKeyPem.isBlank()) {
                recipientKey = csvEncryptionService.parsePublicKeyPem(producerPublicKeyPem);
                log.info("  Using producer's RSA public key for result encryption");
            } else {
                log.info("  No producer public key provided, using own key pair");
            }
            CsvEncryptionService.EncryptedPayload encryptedResult
                    = csvEncryptionService.encrypt(resultCsv, recipientKey);

            // Store encrypted result in MinIO
            minioService.storeProcessedFile(batchId, encryptedResult.encryptedContent().getBytes());

            log.info("  ✅ Batch upload [{}] completed: {} approved, {} declined out of {}",
                    batchId, approved, declined, rows.size());

            return new BatchUploadResult(batchId, "COMPLETED", rows.size(), approved, declined,
                    encryptedResult.encryptedAesKey(), encryptedResult.encryptedContent(),
                    encryptedResult.iv(), null);

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("  ❌ Batch upload [{}] failed: {}", batchId, e.getMessage(), e);
            job.setStatus(BatchStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            batchJobRepository.save(job);
            return new BatchUploadResult(batchId, "FAILED", 0, 0, 0, null, null, null, e.getMessage());
        }
    }

    // ---- Records ----
    public record BatchUploadResult(
            String batchId, String status, int totalRecords, int approvedCount, int declinedCount,
            String encryptedAesKey, String encryptedContent, String iv, String errorMessage) {

    }

    public record ResultCsvRow(
            String transactionId, String merchantId, String merchantCustomer, String maskedPan,
            String amountCents, String currency, String actualBillingDate, String recurringReference,
            String authResult, String decisionReason) {

    }

    // ---- Helpers ----
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

    /**
     * Build result CSV: original columns + auth_result + decision_reason
     */
    private String buildResultCsv(List<ResultCsvRow> rows) {
        StringWriter writer = new StringWriter();
        writer.write("transaction_id,merchant_id,merchant_customer,masked_pan,amount_cents,currency,actual_billing_date,recurring_reference,auth_result,decision_reason\n");
        for (ResultCsvRow row : rows) {
            writer.write(String.join(",",
                    escapeCsv(row.transactionId()),
                    escapeCsv(row.merchantId()),
                    escapeCsv(row.merchantCustomer()),
                    escapeCsv(row.maskedPan()),
                    escapeCsv(row.amountCents()),
                    escapeCsv(row.currency()),
                    escapeCsv(row.actualBillingDate()),
                    escapeCsv(row.recurringReference()),
                    escapeCsv(row.authResult()),
                    escapeCsv(row.decisionReason())
            ));
            writer.write("\n");
        }
        return writer.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void saveInChunks(List<TransactionRecord> records) {
        for (int i = 0; i < records.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, records.size());
            transactionRecordRepository.saveAll(records.subList(i, end));
            transactionRecordRepository.flush();
        }
    }
}
