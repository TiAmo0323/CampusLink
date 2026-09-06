ALTER TABLE `user` ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE `user` ADD COLUMN locked_until DATETIME NULL;
ALTER TABLE `user` ADD COLUMN token_version INT NOT NULL DEFAULT 0;
ALTER TABLE user_skill ADD COLUMN available_time VARCHAR(255) NULL;
ALTER TABLE task ADD COLUMN estimated_duration_minutes INT NOT NULL DEFAULT 60;
ALTER TABLE task ADD COLUMN abnormal_reason VARCHAR(50) NULL;
ALTER TABLE task ADD COLUMN cancel_reason VARCHAR(255) NULL;
ALTER TABLE task ADD COLUMN is_delisted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE task ADD COLUMN delist_reason VARCHAR(255) NULL;
ALTER TABLE task_order ADD COLUMN assignment_round INT NOT NULL DEFAULT 1;
ALTER TABLE task_order ADD COLUMN due_at DATETIME NULL;
ALTER TABLE task_completion ADD COLUMN submitter_id BIGINT NULL;
ALTER TABLE task_completion ADD COLUMN assignment_round INT NOT NULL DEFAULT 1;
ALTER TABLE skill_exchange ADD COLUMN provider_completed_at DATETIME NULL;
ALTER TABLE point_transaction ADD COLUMN before_frozen INT NULL;
ALTER TABLE point_transaction ADD COLUMN after_frozen INT NULL;
CREATE TABLE refresh_session (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at DATETIME NOT NULL, created_at DATETIME NOT NULL,
 CONSTRAINT fk_refresh_user FOREIGN KEY(user_id) REFERENCES `user`(id)
);
CREATE INDEX ix_refresh_user ON refresh_session(user_id);
CREATE INDEX ix_refresh_expiry ON refresh_session(expires_at);
CREATE TABLE order_assignment_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL,
 previous_accepter_id BIGINT NOT NULL, new_accepter_id BIGINT NOT NULL, previous_round INT NOT NULL,
 previous_started_at DATETIME NULL, previous_due_at DATETIME NULL, reason VARCHAR(255) NOT NULL, created_at DATETIME NOT NULL,
 CONSTRAINT fk_assignment_order FOREIGN KEY(order_id) REFERENCES task_order(id),
 CONSTRAINT fk_assignment_previous FOREIGN KEY(previous_accepter_id) REFERENCES `user`(id),
 CONSTRAINT fk_assignment_new FOREIGN KEY(new_accepter_id) REFERENCES `user`(id)
);
ALTER TABLE task_completion ADD CONSTRAINT fk_completion_submitter FOREIGN KEY(submitter_id) REFERENCES `user`(id);
ALTER TABLE task ADD CONSTRAINT ck_task_duration CHECK(estimated_duration_minutes BETWEEN 1 AND 10080);
CREATE INDEX ix_task_feed ON task(is_delisted,status,created_at);
CREATE INDEX ix_application_user ON task_application(applicant_id,created_at);
CREATE INDEX ix_order_accepter ON task_order(accepter_id,status,completed_at);
CREATE INDEX ix_review_recipient ON review(reviewee_id,rating);
CREATE INDEX ix_assignment_previous ON order_assignment_history(previous_accepter_id,order_id);
-- Keep unknown historical frozen balances NULL rather than fabricate audit data.
