CREATE INDEX ix_task_recruiting_deadline ON task(is_delisted,status,application_deadline,id DESC);
CREATE INDEX ix_refresh_user_expiry ON refresh_session(user_id,expires_at);
ALTER TABLE credit_record ADD CONSTRAINT ck_credit_delta CHECK(change_value = after_score - before_score);
ALTER TABLE point_transaction ADD CONSTRAINT ck_point_delta CHECK(change_amount = after_balance - before_balance);
ALTER TABLE point_transaction ADD CONSTRAINT ck_point_has_change CHECK(
 before_frozen IS NULL OR after_frozen IS NULL OR before_balance <> after_balance OR before_frozen <> after_frozen
);
