-- =============================================================
-- perf-seed: 실측 분포 기반 합성 퀴즈 데이터 시드 (research D9 / FR-011 / SC-007)
-- 대상: 로컬 Docker MySQL (운영 금지 — seed.sh 환경 가드가 강제)
-- 식별: problem_set.session_id = 'seed-<id>' (스키마 변경 없이 식별/정리)
-- 입력: @scale (기본 10) — 현재 실측(problem 66,684행)의 배수
-- =============================================================

-- 한글 필러 바이트 크기(3B/자)를 실측과 맞추기 위해 연결 문자셋을 utf8mb4로 고정
SET NAMES utf8mb4;

SET @scale     = COALESCE(@scale, 10);
SET @baseline  = 66684;      -- 실측 problem 행 수
SET @per_set   = 16;         -- 세트당 문항 수(실측 평균 15.6 근사, 고정)
SET @base      = COALESCE(@base, 1000000);  -- 시드 세트 id 오프셋(override 가능). 기본 1000000; 실데이터 id가 이미 그 대역이면 `SET @base := <max_id 초과값>` 선주입
SET @n_sets    = CEIL(@scale * @baseline / @per_set);
SET @target    = @n_sets * @per_set;   -- 총 문항 수(= n_sets * 16)

-- 0..(@target-1) 시퀀스 생성 (6자리 숫자 크로스조인 = 최대 100만, seed.sh가 상한 검증)
DROP TEMPORARY TABLE IF EXISTS seed_seq;
CREATE TEMPORARY TABLE seed_seq (seq INT PRIMARY KEY);
INSERT INTO seed_seq
SELECT seq FROM (
  SELECT d0.n + d1.n*10 + d2.n*100 + d3.n*1000 + d4.n*10000 + d5.n*100000 AS seq
  FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d5
) g
WHERE seq < @target;

-- ── 세트 삽입 (유형 비율: seq%200 → M86 B68 O34 E9 R3 = 43/34/17/4.5/1.5%) ──
INSERT INTO problem_set
  (id, title, user_id, generation_status, quiz_type, total_quiz_count,
   session_id, file_url, custom_instruction, created_at)
SELECT
  @base + seq,
  CONCAT('시드 세트 ', seq),
  'seed-user',
  'COMPLETED',
  CASE
    WHEN (seq % 200) < 86  THEN 'MULTIPLE'
    WHEN (seq % 200) < 154 THEN 'BLANK'
    WHEN (seq % 200) < 188 THEN 'OX'
    WHEN (seq % 200) < 197 THEN 'ESSAY'
    ELSE 'REAL_BLANK'
  END,
  @per_set,
  CONCAT('seed-', @base + seq),
  'seed://perf',
  NULL,
  NOW(6)
FROM seed_seq
WHERE seq < @n_sets;

-- ── 문항 삽입 (유형별 실측 바이트 크기 재현, explanation_content는 ±10% 지터) ──
-- 한글 필러 단위 '가나다라마바사아자차 ' = 31바이트. REPEAT(unit, ROUND(T/31)) ≈ T바이트.
INSERT INTO problem
  (problem_set_id, number, title, selections, explanation_content,
   referenced_pages, applied_instruction, created_at)
SELECT
  s.set_id,
  s.num,
  -- title (실측: M356 B198 O204 E243 R191)
  REPEAT('가나다라마바사아자차 ',
    GREATEST(1, ROUND(CASE s.qt WHEN 'MULTIPLE' THEN 356 WHEN 'BLANK' THEN 198
                                 WHEN 'OX' THEN 204 WHEN 'ESSAY' THEN 243 ELSE 191 END / 31))),
  -- selections: 유효 JSON(List<Selection{content,explanation,correct}>), 정답 1개, 크기 근사
  CASE s.qt
    WHEN 'MULTIPLE' THEN CONCAT(
      '[{"content":"보기1","explanation":"', REPEAT('가나다라마바사아자차 ', 24),
      '","correct":true},{"content":"보기2","explanation":"오답 설명","correct":false}',
      ',{"content":"보기3","explanation":"오답 설명","correct":false}',
      ',{"content":"보기4","explanation":"오답 설명","correct":false}]')
    WHEN 'OX' THEN CONCAT(
      '[{"content":"참","explanation":"', REPEAT('가나다라마바사아자차 ', 3),
      '","correct":true},{"content":"거짓","explanation":"","correct":false}]')
    WHEN 'ESSAY' THEN CONCAT(
      '[{"content":"', REPEAT('가나다라마바사아자차 ', 18),
      '","explanation":"모범답안 해설","correct":true}]')
    ELSE CONCAT(  -- BLANK / REAL_BLANK: 정답 토큰 1개
      '[{"content":"', REPEAT('가나다라마바사아자차 ', 5),
      '","explanation":"","correct":true}]')
  END,
  -- explanation_content (lazy 대상·데모 핵심 컬럼) 실측: M2396 B1719 O1127 E1703 R1709, ±10%
  REPEAT('가나다라마바사아자차 ',
    GREATEST(1, ROUND(
      (CASE s.qt WHEN 'MULTIPLE' THEN 2396 WHEN 'BLANK' THEN 1719
                 WHEN 'OX' THEN 1127 WHEN 'ESSAY' THEN 1703 ELSE 1709 END)
      * (0.9 + RAND() * 0.2) / 31))),
  -- referenced_pages: JSON int 배열 (IntegerListConverter)
  CONCAT('[', FLOOR(RAND()*30)+1, ',', FLOOR(RAND()*30)+1, ']'),
  -- applied_instruction (실측: M80 B36 O22 E85 R83)
  REPEAT('가나다라마 ',
    GREATEST(1, ROUND(CASE s.qt WHEN 'MULTIPLE' THEN 80 WHEN 'BLANK' THEN 36
                                 WHEN 'OX' THEN 22 WHEN 'ESSAY' THEN 85 ELSE 83 END / 16))),
  NOW(6)
FROM (
  SELECT
    seq,
    @base + FLOOR(seq / @per_set) AS set_id,
    (seq % @per_set) + 1          AS num,
    CASE
      WHEN (FLOOR(seq / @per_set) % 200) < 86  THEN 'MULTIPLE'
      WHEN (FLOOR(seq / @per_set) % 200) < 154 THEN 'BLANK'
      WHEN (FLOOR(seq / @per_set) % 200) < 188 THEN 'OX'
      WHEN (FLOOR(seq / @per_set) % 200) < 197 THEN 'ESSAY'
      ELSE 'REAL_BLANK'
    END AS qt
  FROM seed_seq
) s;

DROP TEMPORARY TABLE IF EXISTS seed_seq;

SELECT CONCAT('시드 완료: 세트 ', @n_sets, '개, 문항 ', @target, '개 (scale=', @scale, ')') AS result;
