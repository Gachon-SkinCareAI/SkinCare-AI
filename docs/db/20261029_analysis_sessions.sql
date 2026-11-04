-- MySQL migration for the analysis MVP. Apply once on a backed-up existing database.
-- Not executed against the user's existing database by this task.
CREATE TABLE analysis_sessions (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  client_request_id VARCHAR(36) NOT NULL,
  case_id VARCHAR(36) NOT NULL,
  status VARCHAR(30) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  turn INT NOT NULL DEFAULT 0,
  image LONGBLOB NOT NULL,
  image_type VARCHAR(30) NOT NULL,
  slots_json LONGTEXT NOT NULL,
  vision_json LONGTEXT NULL,
  question_json LONGTEXT NULL,
  result_json LONGTEXT NULL,
  error_code VARCHAR(50) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  CONSTRAINT uq_analysis_create UNIQUE (user_id, client_request_id),
  INDEX ix_analysis_owner_created (user_id, created_at),
  CONSTRAINT fk_analysis_owner FOREIGN KEY (user_id) REFERENCES users(user_id)
);
CREATE TABLE analysis_answers (
  id VARCHAR(36) PRIMARY KEY,
  session_id VARCHAR(36) NOT NULL,
  client_request_id VARCHAR(36) NOT NULL,
  question_id VARCHAR(100) NOT NULL,
  question_text VARCHAR(2000) NULL,
  answer VARCHAR(100) NULL,
  answer_label VARCHAR(200) NULL,
  user_text VARCHAR(2000) NULL,
  turn INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  CONSTRAINT uq_analysis_answer UNIQUE (session_id, client_request_id),
  CONSTRAINT fk_analysis_answer FOREIGN KEY (session_id) REFERENCES analysis_sessions(id) ON DELETE CASCADE
);
