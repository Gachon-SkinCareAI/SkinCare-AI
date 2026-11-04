-- MySQL: apply once after backup and the 20261029 migrations.
-- Not executed automatically by this implementation. DDL may auto-commit.
-- Check this query first; duplicate answers require manual review, never automatic deletion:
-- SELECT inquiry_id, COUNT(*) FROM inquiry_answers GROUP BY inquiry_id HAVING COUNT(*) > 1;

ALTER TABLE inquiries
    ADD COLUMN client_request_id VARCHAR(36) NULL,
    ADD COLUMN analysis_id VARCHAR(36) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uk_inquiry_user_request UNIQUE (user_id, client_request_id),
    ADD INDEX ix_inquiry_owner_created (user_id, created_at);

ALTER TABLE inquiry_answers
    ADD COLUMN admin_id VARCHAR(36) NULL;
-- The existing @OneToOne schema may already have a unique inquiry_id index.
-- If SHOW INDEX FROM inquiry_answers shows one, omit the following redundant index:
ALTER TABLE inquiry_answers ADD CONSTRAINT uk_inquiry_answer_inquiry UNIQUE (inquiry_id);

ALTER TABLE hospitals
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN source_url VARCHAR(1000) NULL,
    ADD COLUMN verified_on DATE NULL,
    ADD INDEX ix_hospital_status_region (status, region);

-- analysis_id is a reference snapshot, deliberately not a foreign key: deleting the
-- user's analysis photo must not delete or block a submitted support inquiry.
-- Existing hospitals without source_url + verified_on remain admin-only until reviewed.
-- Rollback: revert the service binaries while retaining these additive nullable/defaulted columns.
-- Do not drop populated columns or inquiries as part of an application rollback.
