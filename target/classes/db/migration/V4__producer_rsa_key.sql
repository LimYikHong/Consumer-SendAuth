-- =====================================================
-- V4: Producer RSA key storage
-- Stores the RSA public key fetched from the producer
-- system (RTA Bank) for encrypting response CSVs
-- =====================================================

CREATE TABLE IF NOT EXISTS producer_rsa_key (
    id                BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_key_pem    TEXT            NOT NULL,
    fingerprint       VARCHAR(50)     NOT NULL,
    fetched_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        DATETIME        NOT NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    requested_by      VARCHAR(100)    NULL,
    INDEX idx_prk_status (status),
    INDEX idx_prk_fetched_at (fetched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
