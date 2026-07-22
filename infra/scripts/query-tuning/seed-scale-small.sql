-- =============================================================
-- 전 테이블 스케일업 (problem 제외) — 원본 행을 그대로 (scale-1)번 복제한다.
--   제약키만 새로 생성: 숫자 PK/FK = 원본 + k×@base, user_id = 'c{k}_' 접두,
--   session_id(UNIQUE) = '-c{k}' 접미. 나머지 컬럼(제목·내용 등)은 원본 그대로 복사.
--   같은 copy(k)끼리 오프셋이 일치해 FK·UNIQUE 정합이 유지된다. problem 은 규모가 커 별도 파일.
-- 전제: x1 순수 원본(복제 없음 → 모든 숫자 PK < @base). 입력: @scale.
-- =============================================================
SET NAMES utf8mb4;
SET @scale = COALESCE(@scale, 10);
SET @mult  = @scale - 1;          -- 복제 배수(0이면 원본만 유지)
SET @base  = 1000000;             -- copy 오프셋(원본 max id ≪ @base)

-- copy 번호 1..@mult (최대 99 = x100)
DROP TEMPORARY TABLE IF EXISTS kmult;
CREATE TEMPORARY TABLE kmult (k INT PRIMARY KEY);
INSERT INTO kmult (k)
SELECT k FROM (
  SELECT a.n + b.n*10 AS k
  FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
  CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
) t WHERE k BETWEEN 1 AND @mult;

-- varchar PK(user·refresh_token) 은 자기복제 피드백을 막으려 원본을 스냅샷 후 복사한다.
DROP TEMPORARY TABLE IF EXISTS _u;  CREATE TEMPORARY TABLE _u  AS SELECT * FROM user;
DROP TEMPORARY TABLE IF EXISTS _rt; CREATE TEMPORARY TABLE _rt AS SELECT * FROM refresh_token;

-- ── user (PK user_id) ──
INSERT INTO user (user_id, created_at, nickname, provider, role)
SELECT CONCAT('c',k.k,'_',u.user_id), u.created_at, u.nickname, u.provider, u.role
FROM _u u CROSS JOIN kmult k;

-- ── refresh_token (PK user_id → 복제 user 와 1:1) ──
INSERT INTO refresh_token (user_id, created_at, expires_at, rt_hash)
SELECT CONCAT('c',k.k,'_',r.user_id), r.created_at, r.expires_at, r.rt_hash
FROM _rt r CROSS JOIN kmult k;

-- ── problem_set (PK id, UNIQUE session_id) ──
INSERT INTO problem_set (id, title, user_id, generation_status, quiz_type, total_quiz_count, session_id, file_url, custom_instruction, created_at)
SELECT ps.id + k.k*@base, ps.title, CONCAT('c',k.k,'_',ps.user_id), ps.generation_status, ps.quiz_type,
       ps.total_quiz_count, CONCAT(ps.session_id,'-c',k.k), ps.file_url, ps.custom_instruction, ps.created_at
FROM problem_set ps CROSS JOIN kmult k WHERE ps.id < @base;

-- ── quiz_history (PK id, UNIQUE(user_id, problem_set_id)) ──
INSERT INTO quiz_history (id, score, created_at, problem_set_id, title, answers, total_time, user_id, status)
SELECT q.id + k.k*@base, q.score, q.created_at, q.problem_set_id + k.k*@base, q.title, q.answers,
       q.total_time, CONCAT('c',k.k,'_',q.user_id), q.status
FROM quiz_history q CROSS JOIN kmult k WHERE q.id < @base;

-- ── essay_grade_log (PK id) ──
INSERT INTO essay_grade_log (id, user_id, problem_set_id, problem_number, question, student_answer, attempt_count, total_score, max_score, element_scores, overall_feedback, evidence_json, created_at)
SELECT e.id + k.k*@base, CONCAT('c',k.k,'_',e.user_id), e.problem_set_id + k.k*@base, e.problem_number,
       e.question, e.student_answer, e.attempt_count, e.total_score, e.max_score, e.element_scores,
       e.overall_feedback, e.evidence_json, e.created_at
FROM essay_grade_log e CROSS JOIN kmult k WHERE e.id < @base;

-- ── board (PK board_id) ──
INSERT INTO board (board_id, created_at, updated_at, view_count, title, user_id, content, status, category)
SELECT b.board_id + k.k*@base, b.created_at, b.updated_at, b.view_count, b.title,
       CONCAT('c',k.k,'_',b.user_id), b.content, b.status, b.category
FROM board b CROSS JOIN kmult k WHERE b.board_id < @base;

-- ── feedback_board (PK feedback_board_id) ──
INSERT INTO feedback_board (feedback_board_id, user_id, content, created_at)
SELECT f.feedback_board_id + k.k*@base, CONCAT('c',k.k,'_',f.user_id), f.content, f.created_at
FROM feedback_board f CROSS JOIN kmult k WHERE f.feedback_board_id < @base;

DROP TEMPORARY TABLE IF EXISTS kmult;
DROP TEMPORARY TABLE IF EXISTS _u;
DROP TEMPORARY TABLE IF EXISTS _rt;
