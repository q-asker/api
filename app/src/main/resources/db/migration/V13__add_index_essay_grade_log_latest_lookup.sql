-- 문제별 최신 채점 결과 조회를 위한 복합 인덱스
-- 쿼리: WHERE user_id = ? AND problem_set_id = ? AND problem_number = ? ORDER BY created_at DESC
-- 기존 단일 컬럼 인덱스(idx_essay_grade_log_user, idx_essay_grade_log_problem_set)를 대체한다
CREATE INDEX idx_essay_grade_log_latest
    ON essay_grade_log (user_id, problem_set_id, problem_number, created_at DESC);

DROP INDEX idx_essay_grade_log_user ON essay_grade_log;
DROP INDEX idx_essay_grade_log_problem_set ON essay_grade_log;
