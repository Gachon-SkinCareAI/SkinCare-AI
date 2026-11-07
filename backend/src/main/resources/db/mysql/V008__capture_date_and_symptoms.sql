-- Optional self-reported metadata. Unknown historical dates remain NULL.
-- created_at still denotes collection time and remains the retention anchor.
ALTER TABLE analysis_sessions
  ADD COLUMN captured_on DATE NULL,
  ADD COLUMN swelling VARCHAR(16) NULL,
  ADD COLUMN exudate VARCHAR(16) NULL;
