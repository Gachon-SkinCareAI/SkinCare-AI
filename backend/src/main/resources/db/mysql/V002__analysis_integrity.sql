-- String identifiers in JPA do not generate these relationships automatically.
ALTER TABLE analysis_sessions
  ADD CONSTRAINT fk_analysis_owner FOREIGN KEY (user_id) REFERENCES users(user_id),
  ADD CONSTRAINT ck_analysis_pain CHECK (pain_score IS NULL OR pain_score BETWEEN 0 AND 10),
  ADD INDEX ix_analysis_owner_case_created (user_id, case_id, created_at),
  ADD INDEX ix_analysis_status_created (status, created_at);
ALTER TABLE analysis_answers
  ADD CONSTRAINT fk_analysis_answer FOREIGN KEY (session_id) REFERENCES analysis_sessions(id) ON DELETE CASCADE;
ALTER TABLE inquiries ADD INDEX ix_inquiry_owner_created (user_id, created_at);
ALTER TABLE hospitals ADD INDEX ix_hospital_status_region (status, region);
