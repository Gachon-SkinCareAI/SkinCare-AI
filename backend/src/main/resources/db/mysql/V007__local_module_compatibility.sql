-- Reconcile entity fields already referenced by the local legacy modules.
-- No existing rows, original photos, labels or models are removed.
ALTER TABLE admins ADD COLUMN email VARCHAR(254) NULL, ADD COLUMN role VARCHAR(30) NULL DEFAULT 'ROLE_ADMIN';
ALTER TABLE users MODIFY COLUMN status ENUM('ACTIVE','DELETED','SUSPENDED') NOT NULL;
ALTER TABLE hospitals ADD COLUMN latitude DOUBLE NULL, ADD COLUMN longitude DOUBLE NULL,
  ADD COLUMN weekday_hours VARCHAR(50) NULL, ADD COLUMN saturday_hours VARCHAR(50) NULL,
  ADD COLUMN sunday_holiday_hours VARCHAR(50) NULL;
ALTER TABLE diagnoses MODIFY COLUMN body_part VARCHAR(20) NOT NULL,
  MODIFY COLUMN risk_level VARCHAR(20) NOT NULL, MODIFY COLUMN status VARCHAR(20) NOT NULL,
  ADD COLUMN ai_label VARCHAR(20) NULL, ADD COLUMN gate_passed BIT(1) NULL,
  ADD COLUMN gate_reason VARCHAR(50) NULL, ADD COLUMN ai_request_id VARCHAR(64) NULL,
  ADD COLUMN model_version VARCHAR(200) NULL, ADD COLUMN rule_ids VARCHAR(500) NULL,
  ADD COLUMN applied_card_ids VARCHAR(500) NULL, ADD COLUMN care_cards TEXT NULL,
  ADD COLUMN questionnaire TEXT NULL, ADD COLUMN training_use BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN review_status VARCHAR(30) NULL DEFAULT 'UNREVIEWED',
  ADD COLUMN reviewed_by VARCHAR(36) NULL, ADD COLUMN reviewed_at DATETIME(6) NULL;
ALTER TABLE diagnosis_metrics ADD COLUMN detect_confidence DOUBLE NULL, ADD COLUMN energy_score DOUBLE NULL;
ALTER TABLE recoveries ADD COLUMN area_ratio_delta DOUBLE NULL,
  ADD COLUMN trend_hint VARCHAR(30) NULL, ADD COLUMN tracking_note TEXT NULL;
ALTER TABLE ai_retrain_histories ADD COLUMN message VARCHAR(500) NULL,
  ADD COLUMN requested_by VARCHAR(36) NULL, ADD COLUMN approved_by VARCHAR(36) NULL,
  ADD COLUMN approved_at DATETIME(6) NULL;
CREATE TABLE diagnosis_action_histories (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, diagnosis_id VARCHAR(36) NOT NULL,
  previous_status VARCHAR(20) NULL, current_status VARCHAR(20) NULL, review_status VARCHAR(30) NULL,
  note TEXT NULL, processed_by VARCHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  CONSTRAINT fk_action_diagnosis FOREIGN KEY (diagnosis_id) REFERENCES diagnoses(diagnosis_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE user_change_histories (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id VARCHAR(36) NOT NULL,
  change_type VARCHAR(20) NOT NULL, previous_value VARCHAR(30) NULL, new_value VARCHAR(30) NOT NULL,
  reason VARCHAR(500) NULL, changed_by VARCHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL, INDEX idx_user_change_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- The existing user_refresh_tokens table remains intact.
CREATE TABLE refresh_tokens (
  token_hash VARCHAR(64) NOT NULL PRIMARY KEY, subject_id VARCHAR(36) NOT NULL,
  role VARCHAR(10) NOT NULL, expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NULL,
  INDEX idx_refresh_token_subject(subject_id,role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
