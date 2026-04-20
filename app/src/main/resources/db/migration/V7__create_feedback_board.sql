CREATE TABLE feedback_board
(
    feedback_board_id BIGINT AUTO_INCREMENT PRIMARY KEY KEY,
    user_id           VARCHAR(255),
    content           LONGTEXT,
    created_at        DATETIME(6) NOT NULL
);
