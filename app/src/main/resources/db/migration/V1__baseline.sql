-- V1: 베이스라인 — 기존 운영 스키마
-- 기존 DB에서는 baseline-on-migrate=true로 건너뜀
-- 새 환경(로컬 초기화)에서만 실행

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
    PRIMARY KEY (reply_id),
    CONSTRAINT fk_reply_board FOREIGN KEY (board_id) REFERENCES board (board_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS problem_set (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    title             VARCHAR(100),
    user_id           VARCHAR(255),
    generation_status VARCHAR(255) NOT NULL,
    quiz_type         VARCHAR(255),
    total_quiz_count  INT NOT NULL,
    session_id        VARCHAR(255) NOT NULL,
    created_at        DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_problem_set_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS problem (
    problem_set_id      BIGINT NOT NULL,
    number              INT NOT NULL,
    title               TEXT,
    selections          TEXT NOT NULL,
    explanation_content TEXT,
    referenced_pages    TEXT NOT NULL,
    created_at          DATETIME(6),
    PRIMARY KEY (problem_set_id, number),
    CONSTRAINT fk_problem_problem_set FOREIGN KEY (problem_set_id) REFERENCES problem_set (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    PRIMARY KEY (id),
    UNIQUE KEY uk_quiz_history_user_problem (user_id, problem_set_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS update_log (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    update_text VARCHAR(255) NOT NULL,
    created_at  DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
