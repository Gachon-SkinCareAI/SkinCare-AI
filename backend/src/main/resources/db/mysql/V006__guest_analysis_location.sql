-- Additive migration. Existing records remain opted out; no photos/models are removed.
ALTER TABLE users
  MODIFY COLUMN provider ENUM('GOOGLE','KAKAO','NAVER','LOCAL','GUEST') NOT NULL,
  ADD COLUMN guest_key_hash VARCHAR(64) NULL,
  ADD COLUMN guest_policy_version VARCHAR(40) NULL,
  ADD COLUMN location_granted BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN location_consent_version VARCHAR(40) NULL,
  ADD COLUMN location_consented_at DATETIME(6) NULL,
  ADD CONSTRAINT uk_users_guest_key UNIQUE (guest_key_hash);
ALTER TABLE analysis_sessions
  ADD COLUMN source_kind VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
  ADD COLUMN training_eligible BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN training_consent_version VARCHAR(40) NULL,
  ADD COLUMN training_consented_at DATETIME(6) NULL,
  ADD COLUMN training_review_status VARCHAR(20) NOT NULL DEFAULT 'NOT_ELIGIBLE';
