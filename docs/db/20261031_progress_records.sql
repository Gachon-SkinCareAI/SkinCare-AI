-- Additive migration, apply once after previous migrations. No data is deleted.
-- Review/backup the target MySQL database first. MySQL DDL auto-commits.
-- Existing records deliberately keep NULL (not a fabricated pain score or note).
ALTER TABLE analysis_sessions
  ADD COLUMN pain_score INTEGER NULL,
  ADD COLUMN progress_note VARCHAR(2000) NULL;
-- Application validates pain_score as an integer from 0 through 10.
