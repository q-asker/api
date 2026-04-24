CREATE TABLE essay_grade_log
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        VARCHAR(255) NULL,
    problem_set_id BIGINT       NOT NULL,
    problem_number INT          NOT NULL,
    question       VARCHAR(500) NOT NULL,
    student_answer TEXT         NOT NULL,
    total_score    INT          NOT NULL,
    max_score      INT          NOT NULL,
    element_scores JSON         NOT NULL,
    overall_feedback TEXT       NULL,
    created_at     DATETIME(6)  NOT NULL,
    INDEX idx_essay_grade_log_user (user_id),
    INDEX idx_essay_grade_log_problem_set (problem_set_id)
);
