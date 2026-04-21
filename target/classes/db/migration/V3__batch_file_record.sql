-- =====================================================
-- V3: Batch file record table for MinIO file tracking
-- Tracks encrypted incoming and result files stored in MinIO
-- =====================================================

CREATE TABLE IF NOT EXISTS batch_file_record (
    id                BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id          VARCHAR(64)     NOT NULL,
    file_type         VARCHAR(20)     NOT NULL,       -- INCOMING, DECRYPTED, RESULT
    bucket_name       VARCHAR(100)    NOT NULL,
    object_key        VARCHAR(255)    NOT NULL,
    file_size         BIGINT          DEFAULT 0,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_bfr_batch_id (batch_id),
    INDEX idx_bfr_file_type (file_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
