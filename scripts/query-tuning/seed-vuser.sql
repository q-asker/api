-- =============================================================
-- seed-vuser.sql — 부하용 "가상 유저" 1명 + 그 유저 소유 리소스 합성.
--   기존 DB 행(실 크기 데이터)을 가져와 가상 유저가 만든 것처럼 재소유시킨다 → loadgen 이 이 유저 토큰으로
--   self-harvest 하면 실 소유 id 로 templated GET(스캔) 을 태울 수 있다. 마스킹 유저와 분리·결정적(격리).
--   PK 는 AUTO_INCREMENT 가 배정한다(실 max 뒤). id 는 loadgen 이 GET 응답에서 발견(harvest)한다.
--   재실행 결정적: 매번 vu 소유를 DELETE→재삽입(내용 동일, id 만 새로 배정). 입력: @vu(기본 vu_loadtest), @k(리소스 수, 기본 30).
-- 전제: 스케일 시딩(seed-scale)으로 원본 유저·리소스가 이미 있어야 한다. 대상 컨테이너에서 실행.
-- =============================================================
SET NAMES utf8mb4;
SET @vu = COALESCE(@vu, 'vu_loadtest');
SET @k  = COALESCE(@k, 30);

-- ── 재실행 결정성: vu 소유 리소스·유저 제거 후 재생성 ──
-- 이전 부하가 vu 로 생성한 세트(POST /generation 실 저장분 — mock 생성은 커밋됨) 정리. FK 순서상 problem 먼저.
DELETE p FROM problem p
  JOIN problem_set ps ON ps.id = p.problem_set_id
 WHERE ps.user_id = @vu;
DELETE FROM problem_set     WHERE user_id = @vu;
DELETE FROM quiz_history    WHERE user_id = @vu;
DELETE FROM quiz_folder     WHERE user_id = @vu;
DELETE FROM essay_grade_log WHERE user_id = @vu;
DELETE FROM board           WHERE user_id = @vu;
DELETE FROM feedback_board  WHERE user_id = @vu;
DELETE FROM user            WHERE user_id = @vu;

INSERT INTO user (user_id, created_at, nickname, provider, role)
VALUES (@vu, NOW(6), 'loadtest_vu', 'GOOGLE', 'ROLE_USER');

-- ── quiz_history: 서로 다른 problem_set 의 실 행 K개를 vu 소유로 복사 (UNIQUE user_id+problem_set_id 회피) ──
INSERT INTO quiz_history (score, created_at, problem_set_id, title, answers, total_time, user_id, status)
SELECT o.score, o.created_at, o.problem_set_id, o.title, o.answers, o.total_time, @vu, o.status
FROM (
  SELECT p.*, ROW_NUMBER() OVER (ORDER BY p.problem_set_id) rn
  FROM (
    SELECT q.*, ROW_NUMBER() OVER (PARTITION BY q.problem_set_id ORDER BY q.id) pr
    FROM quiz_history q WHERE q.user_id <> @vu
  ) p WHERE p.pr = 1
) o WHERE o.rn <= @k;

-- ── quiz_folder: 실 행 K개 복사 ──
INSERT INTO quiz_folder (user_id, name, created_at)
SELECT @vu, t.name, t.created_at
FROM (SELECT f.name, f.created_at, ROW_NUMBER() OVER (ORDER BY f.id) rn
      FROM quiz_folder f WHERE f.user_id <> @vu) t WHERE t.rn <= @k;

-- ── essay_grade_log: 실 행 K개 복사(원문 크기 유지) ──
INSERT INTO essay_grade_log (user_id, problem_set_id, problem_number, question, student_answer, attempt_count, total_score, max_score, element_scores, overall_feedback, evidence_json, created_at)
SELECT @vu, t.problem_set_id, t.problem_number, t.question, t.student_answer, t.attempt_count, t.total_score, t.max_score, t.element_scores, t.overall_feedback, t.evidence_json, t.created_at
FROM (SELECT e.*, ROW_NUMBER() OVER (ORDER BY e.id) rn
      FROM essay_grade_log e WHERE e.user_id <> @vu) t WHERE t.rn <= @k;

-- ── board: vu 작성 글 K개(실 제목·본문 크기 유지) ──
INSERT INTO board (created_at, updated_at, view_count, title, user_id, content, status, category)
SELECT t.created_at, t.updated_at, t.view_count, t.title, @vu, t.content, t.status, t.category
FROM (SELECT b.*, ROW_NUMBER() OVER (ORDER BY b.board_id) rn
      FROM board b WHERE b.user_id <> @vu) t WHERE t.rn <= @k;

-- ── feedback_board: vu 작성 피드백 K개 ──
INSERT INTO feedback_board (user_id, content, created_at)
SELECT @vu, t.content, t.created_at
FROM (SELECT f.*, ROW_NUMBER() OVER (ORDER BY f.feedback_board_id) rn
      FROM feedback_board f WHERE f.user_id <> @vu) t WHERE t.rn <= @k;
