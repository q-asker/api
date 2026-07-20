-- =============================================================
-- 전 테이블 스케일업 (작은 테이블) — 각 테이블 seed = x1실측 × (scale-1)
--   원본(비-seed) 유지 + seed 추가 → 총량 = x1 × scale.
--   FK 정합: user → problem_set → (quiz_history·essay_grade_log·refresh_token·board·feedback_board).
--   problem 은 규모가 커 seed-scale-problem.sql(배치)로 별도.
-- 입력: @scale (기본 10). 식별: user_id LIKE 'seed-u-%' / session_id LIKE 'seed-%'.
-- 마스킹 컬럼(STEP1 REDACT)은 seed 합성값(''·NULL·'[]')으로 채운다.
-- =============================================================
SET NAMES utf8mb4;
SET @scale = COALESCE(@scale, 10);
SET @mult  = @scale - 1;                 -- seed 배수
SET @base  = 1000000;                    -- seed PK 오프셋(원본 max id ~16000과 무충돌)
-- x1 실측 행수
SET @u=166, @ps=4553, @qh=1828, @rt=155, @eg=642, @bd=11, @fb=21;
SET @uN = @u*@mult;                      -- seed user 수(라운드로빈 분모)
SET @psN = @ps*@mult;                    -- seed problem_set 수(라운드로빈 분모)

-- 0..(psN-1) 시퀀스 (6자리 크로스조인 = 최대 100만; problem_set seed 최대 x100=450,747 < 100만)
DROP TEMPORARY TABLE IF EXISTS seq;
CREATE TEMPORARY TABLE seq (n INT PRIMARY KEY);
INSERT INTO seq
SELECT g.seq FROM (
  SELECT d0.n + d1.n*10 + d2.n*100 + d3.n*1000 + d4.n*10000 + d5.n*100000 AS seq
  FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d5
) g WHERE g.seq < @psN;

-- ── user ──
INSERT INTO user (user_id, created_at, nickname, provider, role)
SELECT CONCAT('seed-u-', n), NOW(6), CONCAT('u_seed', n), 'seed', 'ROLE_USER'
FROM seq WHERE n < @uN;

-- ── refresh_token (user_id 가 PK → seed user 처음 rt*mult 개에 1:1) ──
INSERT INTO refresh_token (user_id, created_at, expires_at, rt_hash)
SELECT CONCAT('seed-u-', n), NOW(6), DATE_ADD(NOW(6), INTERVAL 14 DAY), SHA2(CONCAT('seed-rt-', n), 256)
FROM seq WHERE n < @rt*@mult;

-- ── problem_set (user_id 라운드로빈, session_id='seed-<id>') ──
INSERT INTO problem_set (id, title, user_id, generation_status, quiz_type, total_quiz_count, session_id, file_url, custom_instruction, created_at)
SELECT @base+n, CONCAT('시드 세트 ', n), CONCAT('seed-u-', n % @uN), 'COMPLETED',
  CASE WHEN (n%200)<86 THEN 'MULTIPLE' WHEN (n%200)<154 THEN 'BLANK' WHEN (n%200)<188 THEN 'OX' WHEN (n%200)<197 THEN 'ESSAY' ELSE 'REAL_BLANK' END,
  16, CONCAT('seed-', @base+n), 'seed://perf', NULL, NOW(6)
FROM seq WHERE n < @psN;

-- ── quiz_history (answers 마스킹 → NULL) ──
INSERT INTO quiz_history (id, score, created_at, problem_set_id, title, answers, total_time, user_id, status)
SELECT @base+n, 80, NOW(6), @base+(n % @psN), CONCAT('시드 풀이 ', n), NULL, '00:10:00', CONCAT('seed-u-', n % @uN), 'COMPLETED'
FROM seq WHERE n < @qh*@mult;

-- ── essay_grade_log (student_answer='' · element_scores='[]' 마스킹) ──
INSERT INTO essay_grade_log (id, user_id, problem_set_id, problem_number, question, student_answer, attempt_count, total_score, max_score, element_scores, overall_feedback, evidence_json, created_at)
SELECT @base+n, CONCAT('seed-u-', n % @uN), @base+(n % @psN), 1, '시드 질문', '', 1, 7, 10, '[]', NULL, NULL, NOW(6)
FROM seq WHERE n < @eg*@mult;

-- ── board (title·content 마스킹 → '') ──
INSERT INTO board (board_id, created_at, updated_at, view_count, title, user_id, content, status, category)
SELECT @base+n, NOW(6), NOW(6), 0, '', CONCAT('seed-u-', n % @uN), '', 'IN_PROGRESS', 'INQUIRY'
FROM seq WHERE n < @bd*@mult;

-- ── feedback_board (content 마스킹 → '') ──
INSERT INTO feedback_board (feedback_board_id, user_id, content, created_at)
SELECT @base+n, CONCAT('seed-u-', n % @uN), '', NOW(6)
FROM seq WHERE n < @fb*@mult;

DROP TEMPORARY TABLE IF EXISTS seq;
