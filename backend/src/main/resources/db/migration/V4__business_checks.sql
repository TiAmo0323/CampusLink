ALTER TABLE `user` ADD CONSTRAINT ck_user_credit CHECK (credit_score BETWEEN 0 AND 100);
ALTER TABLE `user` ADD CONSTRAINT ck_user_points CHECK (available_points >= 0 AND frozen_points >= 0);
ALTER TABLE task ADD CONSTRAINT ck_task_reward CHECK (reward_points > 0);
ALTER TABLE task ADD CONSTRAINT ck_task_credit CHECK (min_credit_score BETWEEN 0 AND 100);
ALTER TABLE review ADD CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5);
