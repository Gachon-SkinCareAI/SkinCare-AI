-- MySQL existing-database migration. Apply once after a backup, before starting the updated backend.
-- Not executed by this change. Fresh databases can be created from the JPA entities.
ALTER TABLE users
    ADD COLUMN token_version VARCHAR(36) NULL,
    ADD COLUMN terms_version VARCHAR(40) NULL,
    ADD COLUMN privacy_version VARCHAR(40) NULL,
    ADD COLUMN consented_at DATETIME(6) NULL;
ALTER TABLE admins ADD COLUMN token_version VARCHAR(36) NULL;
-- Existing users must explicitly accept the current policy; old booleans are not evidence of this version.
-- All previously issued JWTs are rejected (missing version claim and changed signing-key derivation).
