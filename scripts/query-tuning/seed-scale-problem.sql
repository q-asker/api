-- =============================================================
-- problem 복제 — 원본 문항 전량을 copy k 로 한 벌 복제한다.
--   problem_set_id = 원본 + @k×@base (같은 copy 의 problem_set 을 가리켜 FK 정합).
--   나머지 컬럼(제목·선지·해설·페이지·지시)은 원본 그대로 복사한다.
--   규모가 커(x100 = 원본×99) copy 단위로 seed-scale.sh 가 반복 호출한다.
-- 전제: 해당 copy 의 problem_set 이 먼저 존재(seed-scale-small.sql). 입력: @k (copy 번호).
-- =============================================================
SET NAMES utf8mb4;
SET @base = 1000000;
SET @k    = COALESCE(@k, 0);

INSERT INTO problem (problem_set_id, number, title, selections, explanation_content, referenced_pages, applied_instruction, created_at)
SELECT p.problem_set_id + @k*@base, p.number, p.title, p.selections, p.explanation_content,
       p.referenced_pages, p.applied_instruction, p.created_at
FROM problem p WHERE p.problem_set_id < @base;
