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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the full batch processing pipeline: 1. Decrypt CSV 2. Parse rows
 * 3. Evaluate each transaction via AuthorizationEngine 4. Persist results
 * (batch insert) 5. Build response message
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchProcessingService {

    private final CsvDecryptionService csvDecryptionService;
    private final AuthorizationEngine authorizationEngine;
    private final BatchJobRepository batchJobRepository;
    private final TransactionRecordRepository transactionRecordRepository;

    @Value("${app.processing.batch-chunk-size:500}")
    private int chunkSize;

    /**
     * Process a batch request end-to-end.
     */
    @Transactional
    public BatchResponseMessage process(BatchRequestMessage request) {
        String batchId = request.getBatchId();
        log.info("▶ Processing batch [{}]", batchId);

        // Idempotency — skip if already processed
        if (batchJobRepository.existsByBatchId(batchId)) {
            log.warn("Batch [{}] already processed, skipping", batchId);
            BatchJob existing = batchJobRepository.findByBatchId(batchId).orElseThrow();
            return buildResponse(existing, transactionRecordRepository.findByBatchId(batchId));
        }

        // 1. Create batch job record
        BatchJob job = BatchJob.builder()
                .batchId(batchId)
                .status(BatchStatus.RECEIVED)
                .build();
        batchJobRepository.save(job);

        try {
            // 2. Decrypt CSV
            job.setStatus(BatchStatus.PROCESSING);
            batchJobRepository.save(job);

            String csvContent = csvDecryptionService.decrypt(
                    request.getEncryptedAesKey(),
                    request.getEncryptedCsvContent(),
                    request.getIv());

            // 3. Parse CSV rows
            List<TransactionCsvRow> rows = parseCsv(csvContent);
            log.info("  Parsed {} transaction rows for batch [{}]", rows.size(), batchId);

            // 4. Process each row and collect records
            List<TransactionRecord> records = new ArrayList<>(rows.size());
            int approved = 0, declined = 0;

            for (TransactionCsvRow row : rows) {
                BigDecimal amount = new BigDecimal(row.getAmount());
                AccountStatus acctStatus = parseAccountStatus(row.getAccountStatus());

                AuthorizationEngine.Decision decision
                        = authorizationEngine.evaluate(amount, acctStatus);

                TransactionRecord record = TransactionRecord.builder()
                        .batchId(batchId)
                        .transactionId(row.getTransactionId())
                        .accountNumber(row.getAccountNumber())
                        .accountStatus(acctStatus)
                        .amount(amount)
                        .currency(row.getCurrency())
                        .merchantName(row.getMerchantName())
                        .merchantCategory(row.getMerchantCategory())
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

            // 5. Bulk-save transaction records (Hibernate batching via batch_size=100)
            saveInChunks(records);

            // 6. Update batch job
            job.setTotalRecords(rows.size());
            job.setApprovedCount(approved);
            job.setDeclinedCount(declined);
            job.setStatus(BatchStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            batchJobRepository.save(job);

            log.info("  ✅ Batch [{}] completed: {} approved, {} declined out of {}",
                    batchId, approved, declined, rows.size());

            return buildResponse(job, records);

        } catch (Exception e) {
            log.error("  ❌ Batch [{}] failed: {}", batchId, e.getMessage(), e);
            job.setStatus(BatchStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            batchJobRepository.save(job);

            return BatchResponseMessage.builder()
                    .batchId(batchId)
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .processedAt(LocalDateTime.now().toString())
                    .build();
        }
    }

    // ---- helpers ----
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

    private AccountStatus parseAccountStatus(String status) {
        if (status == null || status.isBlank()) {
            return AccountStatus.ACTIVE;
        }
        try {
            return AccountStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown account status '{}', defaulting to ACTIVE", status);
            return AccountStatus.ACTIVE;
        }
    }

    /**
     * Save records in chunks to leverage Hibernate JDBC batching efficiently
     * and avoid OutOfMemoryError for very large batches.
     */
    private void saveInChunks(List<TransactionRecord> records) {
        for (int i = 0; i < records.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, records.size());
            transactionRecordRepository.saveAll(records.subList(i, end));
            transactionRecordRepository.flush();
        }
    }

    private BatchResponseMessage buildResponse(BatchJob job, List<TransactionRecord> records) {
        List<TransactionResultDto> resultDtos = records.stream()
                .map(r -> TransactionResultDto.builder()
                .transactionId(r.getTransactionId())
                .accountNumber(r.getAccountNumber())
                .amount(r.getAmount().toPlainString())
                .authResult(r.getAuthResult().name())
                .decisionReason(r.getDecisionReason())
                .build())
                .toList();

        return BatchResponseMessage.builder()
                .batchId(job.getBatchId())
                .status(job.getStatus().name())
                .totalRecords(job.getTotalRecords())
                .approvedCount(job.getApprovedCount())
                .declinedCount(job.getDeclinedCount())
                .processedAt(LocalDateTime.now().toString())
                .results(resultDtos)
                .build();
    }
}
