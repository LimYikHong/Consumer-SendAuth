package com.worldline.mock.service;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * MinIO object storage service for batch CSV files. Stores both incoming
 * (encrypted) and processed (result) files.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;

    @Value("${app.minio.bucket.name}")
    private String bucketName;

    /**
     * Store the incoming encrypted CSV file.
     */
    public void storeIncomingFile(String batchId, byte[] content) {
        putObject(bucketName, buildKey(batchId, "incoming"), content, "application/octet-stream");
        log.info("📦 Stored incoming file for batch [{}] in MinIO ({} bytes)", batchId, content.length);
    }

    /**
     * Store the processed/result encrypted CSV file.
     */
    public void storeProcessedFile(String batchId, byte[] content) {
        putObject(bucketName, buildKey(batchId, "result"), content, "application/octet-stream");
        log.info("📦 Stored result file for batch [{}] in MinIO ({} bytes)", batchId, content.length);
    }

    /**
     * Store plaintext CSV (decrypted) for audit/reference.
     */
    public void storePlaintextCsv(String batchId, String csvContent) {
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);
        putObject(bucketName, buildKey(batchId, "decrypted"), bytes, "text/csv");
        log.debug("📦 Stored decrypted CSV for batch [{}] ({} bytes)", batchId, bytes.length);
    }

    private void putObject(String bucket, String key, byte[] data, String contentType) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(bais, data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("Failed to store object {}/{}: {}", bucket, key, e.getMessage());
            throw new RuntimeException("MinIO upload failed", e);
        }
    }

    private String buildKey(String batchId, String suffix) {
        return batchId + "/" + suffix + ".csv";
    }
}
