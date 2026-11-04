-- Additive migration: no model, label, account or analysis rows are removed.
ALTER TABLE users
  ADD COLUMN health_consent_version VARCHAR(40) NULL,
  ADD COLUMN health_consented_at DATETIME(6) NULL,
  ADD COLUMN training_consent_version VARCHAR(40) NULL,
  ADD COLUMN training_consented_at DATETIME(6) NULL,
  ADD COLUMN training_granted BIT(1) NOT NULL DEFAULT b'0';
CREATE TABLE consent_events (
  id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, purpose VARCHAR(30) NOT NULL,
  policy_version VARCHAR(40) NOT NULL, granted BIT(1) NOT NULL, occurred_at DATETIME(6) NOT NULL,
  INDEX ix_consent_owner_time(user_id,occurred_at),
  CONSTRAINT fk_consent_owner FOREIGN KEY(user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE account_erasure_receipts (
  id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL UNIQUE, requested_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE admin_audit_events (
  id VARCHAR(36) PRIMARY KEY, actor_id VARCHAR(36) NOT NULL, action VARCHAR(50) NOT NULL,
  target_id VARCHAR(36) NOT NULL, detail VARCHAR(100) NULL, occurred_at DATETIME(6) NOT NULL,
  INDEX ix_audit_time(occurred_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE content_reviews (
  id VARCHAR(36) PRIMARY KEY, version BIGINT NOT NULL, kind VARCHAR(30) NOT NULL,
  title VARCHAR(200) NOT NULL, content MEDIUMTEXT NOT NULL, content_hash VARCHAR(64) NOT NULL,
  source_url VARCHAR(1000) NOT NULL, supersedes_id VARCHAR(36) NULL,
  author_id VARCHAR(36) NOT NULL, status VARCHAR(20) NOT NULL, created_at DATETIME(6) NOT NULL,
  submitted_at DATETIME(6) NULL, reviewer_id VARCHAR(36) NULL, decided_at DATETIME(6) NULL,
  reason VARCHAR(2000) NULL, evidence_url VARCHAR(1000) NULL,
  INDEX ix_review_status_time(status,created_at,id),
  CONSTRAINT fk_review_previous FOREIGN KEY(supersedes_id) REFERENCES content_reviews(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
