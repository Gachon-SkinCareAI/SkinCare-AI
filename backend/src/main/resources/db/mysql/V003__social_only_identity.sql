-- Preserve all historical users, passwords, consents and analysis foreign keys.
-- Email is optional profile data, never an identity/linking key.
ALTER TABLE users
    MODIFY COLUMN email VARCHAR(254) NULL,
    MODIFY COLUMN provider ENUM('GOOGLE','KAKAO','NAVER','LOCAL') NOT NULL,
    DROP INDEX UK6dotkott2kjsp8vw4d0m25fb7,
    ADD COLUMN social_subject_hash VARCHAR(64) NULL,
    ADD CONSTRAINT uk_users_social_identity UNIQUE (provider, social_subject_hash);
