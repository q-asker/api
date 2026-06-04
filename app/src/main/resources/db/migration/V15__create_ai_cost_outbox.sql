-- AI 비용 이벤트 Transactional Outbox (ai_cost_outbox)
-- ai_invocation 적재와 동일 트랜잭션으로 INSERT되어 dual-write 손실 0을 보장한다.
-- MessageRelay가 status=PENDING 행을 1초 주기로 폴링하여 ai.cost.raw에 발행하고 SENT로 전이한다.
CREATE TABLE ai_cost_outbox
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id    CHAR(36)     NOT NULL,
    aggregate_key VARCHAR(255) NOT NULL,
    payload       LONGTEXT     NOT NULL,
    status        VARCHAR(255) NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    sent_at       DATETIME(6)  NULL,
    -- Relay 폴링 효율: PENDING 행을 생성 순서대로 조회
    INDEX idx_ai_cost_outbox_poll (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
