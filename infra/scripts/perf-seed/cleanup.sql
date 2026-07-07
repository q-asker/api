-- =============================================================
-- perf-seed 정리: 'seed-' 접두사 세트와 그 문항을 일괄 삭제 (원상 복구)
-- FK(problem → problem_set) 때문에 자식(problem) 먼저 삭제
-- =============================================================

DELETE p FROM problem p
JOIN problem_set ps ON ps.id = p.problem_set_id
WHERE ps.session_id LIKE 'seed-%';

DELETE FROM problem_set WHERE session_id LIKE 'seed-%';

SELECT CONCAT('정리 완료: 남은 seed 세트 ',
  (SELECT COUNT(*) FROM problem_set WHERE session_id LIKE 'seed-%'), '개') AS result;
