CREATE INDEX idx_quiz_history_user_created
    ON quiz_history (user_id, created_at DESC);
