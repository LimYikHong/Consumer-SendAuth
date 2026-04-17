-- =====================================================
-- V1: Initial schema for Mock Authorization Service
-- Database: consumer_db (MySQL)
-- Designed for efficient batch inserts and lookups
-- =====================================================

CREATE TABLE IF NOT EXISTS batch_job (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id        VARCHAR(64)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    total_records   INT             DEFAULT 0,
    approved_count  INT             DEFAULT 0,
    declined_count  INT             DEFAULT 0,
    received_at     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME        NULL,
    error_message   VARCHAR(1000)   NULL,
    UNIQUE KEY uk_batch_job_batch_id (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_batch_job_status ON batch_job(status);

CREATE TABLE IF NOT EXISTS transaction_record (
    id                BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id          VARCHAR(64)     NOT NULL,
    transaction_id    VARCHAR(64)     NOT NULL,
    account_number    VARCHAR(32)     NOT NULL,
    account_status    VARCHAR(20)     NOT NULL,
    amount            DECIMAL(15,2)   NOT NULL,
    currency          VARCHAR(3)      NULL,
    merchant_name     VARCHAR(100)    NULL,
    merchant_category VARCHAR(20)     NULL,
    auth_result       VARCHAR(20)     NOT NULL,
    decision_reason   VARCHAR(200)    NULL,
    processed_at      DATETIME        DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_txn_batch_id       ON transaction_record(batch_id);
CREATE INDEX idx_txn_account_number ON transaction_record(account_number);
CREATE INDEX idx_txn_result         ON transaction_record(auth_result);
CREATE INDEX idx_txn_batch_result   ON transaction_record(batch_id, auth_result);
