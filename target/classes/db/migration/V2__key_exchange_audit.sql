-- =====================================================
-- V2: Key exchange audit table
-- Tracks every public key request from producer system
-- =====================================================

CREATE TABLE IF NOT EXISTS key_exchange_audit (
    id                BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requester_ip      VARCHAR(45)     NOT NULL,
    endpoint          VARCHAR(100)    NOT NULL,
    status            VARCHAR(20)     NOT NULL,
    key_fingerprint   VARCHAR(50)     NULL,
    failure_reason    VARCHAR(200)    NULL,
    user_agent        VARCHAR(255)    NULL,
    requested_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_kea_requested_at ON key_exchange_audit(requested_at);
CREATE INDEX idx_kea_status       ON key_exchange_audit(status);
