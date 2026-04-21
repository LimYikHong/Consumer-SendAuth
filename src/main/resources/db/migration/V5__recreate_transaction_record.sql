-- =====================================================
-- V5: Recreate transaction_record to match producer CSV columns
-- Old columns: account_number, account_status, amount, merchant_name, merchant_category
-- New columns: merchant_id, merchant_customer, masked_pan, amount_cents, actual_billing_date, recurring_reference
-- =====================================================

DROP TABLE IF EXISTS transaction_record;

CREATE TABLE transaction_record (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id            VARCHAR(64)     NOT NULL,
    transaction_id      VARCHAR(64)     NOT NULL,
    merchant_id         VARCHAR(64)     NOT NULL,
    merchant_customer   VARCHAR(100)    NULL,
    masked_pan          VARCHAR(32)     NULL,
    amount_cents        BIGINT          NOT NULL DEFAULT 0,
    currency            VARCHAR(3)      NULL,
    actual_billing_date VARCHAR(30)     NULL,
    recurring_reference VARCHAR(100)    NULL,
    auth_result         VARCHAR(20)     NOT NULL,
    decision_reason     VARCHAR(200)    NULL,
    processed_at        DATETIME        DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_txn_batch_id      ON transaction_record(batch_id);
CREATE INDEX idx_txn_merchant_id   ON transaction_record(merchant_id);
CREATE INDEX idx_txn_result        ON transaction_record(auth_result);
CREATE INDEX idx_txn_batch_result  ON transaction_record(batch_id, auth_result);
