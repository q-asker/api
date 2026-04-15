-- V1: 베이스라인 — 기존 운영 스키마
-- 모든 CREATE/INDEX/FK에 IF NOT EXISTS를 사용하여 기존 DB와 충돌 방지

CREATE TABLE IF NOT EXISTS user (
    user_id  VARCHAR(255) NOT NULL,
    role     VARCHAR(255),
    provider VARCHAR(255),
    nickname VARCHAR(255),
    created_at DATETIME(6),
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS refresh_token (
    user_id    VARCHAR(255) NOT NULL,
    rt_hash    VARCHAR(255),
    expires_at DATETIME(6),
    created_at DATETIME(6),
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS board (
    board_id   BIGINT NOT NULL AUTO_INCREMENT,
    user_id    VARCHAR(255) NOT NULL,
    title      VARCHAR(100) NOT NULL,
    content    LONGTEXT NOT NULL,
    view_count BIGINT,
    status     VARCHAR(255),
    updated_at DATETIME(6),
    created_at DATETIME(6),
    PRIMARY KEY (board_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reply (
    reply_id   BIGINT NOT NULL AUTO_INCREMENT,
    board_id   BIGINT NOT NULL,
    admin_id   VARCHAR(255) NOT NULL,
    content    LONGTEXT NOT NULL,
    created_at DATETIME(6),
    PRIMARY KEY (reply_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- reply → board FK (이미 존재하면 무시)
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'reply'
    AND CONSTRAINT_NAME = 'fk_reply_board' AND CONSTRAINT_TYPE = 'FOREIGN KEY');
SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE reply ADD CONSTRAINT fk_reply_board FOREIGN KEY (board_id) REFERENCES board (board_id) ON DELETE CASCADE',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS problem_set (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    title             VARCHAR(100),
    user_id           VARCHAR(255),
    generation_status VARCHAR(255) NOT NULL,
    quiz_type         VARCHAR(255),
    total_quiz_count  INT NOT NULL,
    session_id        VARCHAR(255) NOT NULL,
    created_at        DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- problem_set.session_id UNIQUE (이미 존재하면 무시)
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'problem_set'
    AND INDEX_NAME = 'uk_problem_set_session');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE problem_set ADD UNIQUE KEY uk_problem_set_session (session_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS problem (
    problem_set_id      BIGINT NOT NULL,
    number              INT NOT NULL,
    title               TEXT,
    selections          TEXT NOT NULL,
    explanation_content TEXT,
    referenced_pages    TEXT NOT NULL,
    created_at          DATETIME(6),
    PRIMARY KEY (problem_set_id, number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- problem → problem_set FK (이미 존재하면 무시)
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'problem'
    AND CONSTRAINT_NAME = 'fk_problem_problem_set' AND CONSTRAINT_TYPE = 'FOREIGN KEY');
SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE problem ADD CONSTRAINT fk_problem_problem_set FOREIGN KEY (problem_set_id) REFERENCES problem_set (id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS quiz_history (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    user_id        VARCHAR(255) NOT NULL,
    problem_set_id BIGINT NOT NULL,
    title          VARCHAR(100),
    answers        JSON,
    score          INT,
    total_time     VARCHAR(255),
    status         VARCHAR(255) NOT NULL,
    created_at     DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- quiz_history UNIQUE (이미 존재하면 무시)
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quiz_history'
    AND INDEX_NAME = 'uk_quiz_history_user_problem');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE quiz_history ADD UNIQUE KEY uk_quiz_history_user_problem (user_id, problem_set_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS update_log (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    update_text VARCHAR(255) NOT NULL,
    created_at  DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
