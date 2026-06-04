-- AI 호출 원장 (ai_invocation)
-- Gemini 호출 1건 = 1행. 사용자별 토큰 사용량 추적의 원천 데이터로, 토큰 수를 영구 보존한다.
-- request_id UNIQUE 제약으로 멱등을 보장한다(동일 호출의 중복 적재 차단).
CREATE TABLE ai_invocation
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id         CHAR(36)       NOT NULL,
    user_id            VARCHAR(255)   NOT NULL,
    quiz_set_id        BIGINT         NULL,
    model              VARCHAR(255)   NOT NULL,
    model_version      VARCHAR(255)   NULL,
    input_tokens       BIGINT         NOT NULL,
    cached_tokens      BIGINT         NOT NULL,
    thinking_tokens    BIGINT         NOT NULL,
    output_tokens      BIGINT         NOT NULL,
    latency_ms         BIGINT         NOT NULL,
    status             VARCHAR(255)   NOT NULL,
    error_code         VARCHAR(255)   NULL,
    occurred_at        DATETIME(6)    NOT NULL,
    created_at         DATETIME(6)    NOT NULL,
    -- 멱등 보장: 호출 단위 UUID 중복 적재 차단
    UNIQUE KEY uk_ai_invocation_request (request_id),
    -- 사용자별 비용 집계(후속 컨슈머/조회) 대비 조회 인덱스
    INDEX idx_ai_invocation_user_occurred (user_id, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
