-- Additive restore-suppression ledger. Existing data and earlier migrations remain unchanged.
CREATE TABLE analysis_erasure_receipts (
  id VARCHAR(36) PRIMARY KEY, session_id VARCHAR(36) NOT NULL UNIQUE, requested_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
