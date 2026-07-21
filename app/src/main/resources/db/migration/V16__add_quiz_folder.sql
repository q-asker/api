-- V16: 퀴즈 기록 폴더 분류
-- 사용자가 자기 기록을 묶는 명명된 폴더(quiz_folder)를 두고,
-- quiz_history.folder_id(nullable FK 컬럼)로 단일 소속을 표현한다. NULL = 미분류.
CREATE TABLE quiz_folder
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    VARCHAR(255) NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    INDEX idx_quiz_folder_user (user_id)
);

ALTER TABLE quiz_history
    ADD COLUMN folder_id BIGINT NULL,
    ADD INDEX idx_quiz_history_user_folder (user_id, folder_id);
