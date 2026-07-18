-- =============================================================
-- 전 테이블 스케일업 (problem 배치) — problem_set [@ps_from, @ps_to) 범위의 문항 16개씩.
--   seed-scale-small.sql 이 만든 seed problem_set(id=@base+idx)에 문항을 채운다.
--   규모가 커(x100 = 720만) 배치 파이프(seed-scale.sh)로 problem_set 청크마다 호출.
-- 입력: @ps_from, @ps_to (problem_set 인덱스 범위). 필러 바이트는 perf-seed 실측 분포.
-- =============================================================
SET NAMES utf8mb4;
SET @base    = 1000000;
SET @ps_from = COALESCE(@ps_from, 0);
SET @ps_to   = COALESCE(@ps_to, 0);
SET @nprob   = (@ps_to - @ps_from) * 16;      -- 이 배치 문항 수(세트당 16)

-- 0..(nprob-1) 시퀀스 (배치당 100만 미만이 되도록 seed-scale.sh 가 step 설정)
DROP TEMPORARY TABLE IF EXISTS pseq;
CREATE TEMPORARY TABLE pseq (n INT PRIMARY KEY);
INSERT INTO pseq
SELECT g.seq FROM (
  SELECT d0.n + d1.n*10 + d2.n*100 + d3.n*1000 + d4.n*10000 + d5.n*100000 AS seq
  FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d5
) g WHERE g.seq < @nprob;

-- 문항 삽입: set_id=@base+@ps_from+FLOOR(n/16), number=n%16+1. 유형은 set 인덱스%200(problem_set과 동일 규칙).
INSERT INTO problem
  (problem_set_id, number, title, selections, explanation_content, referenced_pages, applied_instruction, created_at)
SELECT
  s.set_id, s.num,
  REPEAT('가나다라마바사아자차 ',
    GREATEST(1, ROUND(CASE s.qt WHEN 'MULTIPLE' THEN 356 WHEN 'BLANK' THEN 198 WHEN 'OX' THEN 204 WHEN 'ESSAY' THEN 243 ELSE 191 END / 31))),
  CASE s.qt
    WHEN 'MULTIPLE' THEN CONCAT('[{"content":"보기1","explanation":"', REPEAT('가나다라마바사아자차 ', 24), '","correct":true},{"content":"보기2","explanation":"오답 설명","correct":false},{"content":"보기3","explanation":"오답 설명","correct":false},{"content":"보기4","explanation":"오답 설명","correct":false}]')
    WHEN 'OX' THEN CONCAT('[{"content":"참","explanation":"', REPEAT('가나다라마바사아자차 ', 3), '","correct":true},{"content":"거짓","explanation":"","correct":false}]')
    WHEN 'ESSAY' THEN CONCAT('[{"content":"', REPEAT('가나다라마바사아자차 ', 18), '","explanation":"모범답안 해설","correct":true}]')
    ELSE CONCAT('[{"content":"', REPEAT('가나다라마바사아자차 ', 5), '","explanation":"","correct":true}]')
  END,
  REPEAT('가나다라마바사아자차 ',
    GREATEST(1, ROUND((CASE s.qt WHEN 'MULTIPLE' THEN 2396 WHEN 'BLANK' THEN 1719 WHEN 'OX' THEN 1127 WHEN 'ESSAY' THEN 1703 ELSE 1709 END) * (0.9 + RAND()*0.2) / 31))),
  CONCAT('[', FLOOR(RAND()*30)+1, ',', FLOOR(RAND()*30)+1, ']'),
  REPEAT('가나다라마 ',
    GREATEST(1, ROUND(CASE s.qt WHEN 'MULTIPLE' THEN 80 WHEN 'BLANK' THEN 36 WHEN 'OX' THEN 22 WHEN 'ESSAY' THEN 85 ELSE 83 END / 16))),
  NOW(6)
FROM (
  SELECT
    @base + @ps_from + FLOOR(n/16) AS set_id,
    (n % 16) + 1                   AS num,
    CASE
      WHEN ((@ps_from + FLOOR(n/16)) % 200) < 86  THEN 'MULTIPLE'
      WHEN ((@ps_from + FLOOR(n/16)) % 200) < 154 THEN 'BLANK'
      WHEN ((@ps_from + FLOOR(n/16)) % 200) < 188 THEN 'OX'
      WHEN ((@ps_from + FLOOR(n/16)) % 200) < 197 THEN 'ESSAY'
      ELSE 'REAL_BLANK'
    END AS qt
  FROM pseq
) s;

DROP TEMPORARY TABLE IF EXISTS pseq;
