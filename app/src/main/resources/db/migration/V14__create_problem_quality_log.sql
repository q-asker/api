-- V14: 문항 품질 로그 테이블 생성 (2-패스 품질 검증·마킹 파이프라인)
-- problem은 순수 서빙 엔티티로 두고, 품질(생성 근거·판정·미달 사유·재생성 원본)을 이 로그로 분리한다.
-- 문항 1:1(problem_set_id, number)로 모든 문항을 커버하며, rationale=NULL 행은 품질 검증 대상에서 제외된다(FR-014).
CREATE TABLE problem_quality_log
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    problem_set_id BIGINT       NOT NULL,
    number         INT          NOT NULL,
    rationale      JSON         NULL,
    quality_status VARCHAR(20)  NULL,
    feedback       TEXT         NULL,
    v1_json        JSON         NULL,
    v1_feedback    TEXT         NULL,
    created_at     DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_pql_set_number (problem_set_id, number),
    INDEX idx_pql_set (problem_set_id)
);
