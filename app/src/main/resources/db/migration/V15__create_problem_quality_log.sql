-- V15: 문항 품질 로그 테이블 생성 (생성 근거·재검토 로그)
-- problem은 순수 서빙 엔티티로 두고, 생성 근거(첫 생성본 v1·재생성 개선본 v2·사후 재검토)를 이 로그로 분리한다.
-- 문항 1:1(problem_set_id, number)로 모든 문항을 커버한다.
CREATE TABLE problem_quality_log
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    problem_set_id   BIGINT       NOT NULL,
    number           INT          NOT NULL,
    v1_question_json TEXT         NULL,
    v1_explanation   TEXT         NULL,
    v1_feedback      TEXT         NULL,
    v2_question_json TEXT         NULL,
    v2_explanation   TEXT         NULL,
    v2_feedback      TEXT         NULL,
    review           TEXT         NULL,
    created_at       DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_pql_set_number (problem_set_id, number),
    INDEX idx_pql_set (problem_set_id)
);
