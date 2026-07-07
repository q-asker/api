-- =============================================================
-- perf-seed 검증: 유형 비율 + explanation_content 크기 분포가 실측 대비 ±10% 이내인지 (SC-007)
-- 출력 status 컬럼에 FAIL이 하나라도 있으면 seed.sh가 비정상 종료 처리
-- =============================================================

-- ① 총 문항 수
SELECT 'row_count' AS check_name,
       COUNT(*)    AS actual,
       'PASS'      AS status
FROM problem p JOIN problem_set ps ON ps.id = p.problem_set_id
WHERE ps.session_id LIKE 'seed-%';

-- ② 유형 비율 (실측: MULTIPLE 43 / BLANK 34 / OX 17 / ESSAY 4.5 / REAL_BLANK 1.5 %) ±10%p 상대
SELECT
  CONCAT('ratio_', ps.quiz_type) AS check_name,
  ROUND(100 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) AS actual_pct,
  CASE ps.quiz_type
    WHEN 'MULTIPLE'   THEN 43.0 WHEN 'BLANK' THEN 34.0 WHEN 'OX' THEN 17.0
    WHEN 'ESSAY'      THEN 4.5  ELSE 1.5 END AS expected_pct,
  CASE WHEN ABS(ROUND(100 * COUNT(*) / SUM(COUNT(*)) OVER (), 2)
              - CASE ps.quiz_type WHEN 'MULTIPLE' THEN 43.0 WHEN 'BLANK' THEN 34.0
                     WHEN 'OX' THEN 17.0 WHEN 'ESSAY' THEN 4.5 ELSE 1.5 END)
            <= 0.1 * CASE ps.quiz_type WHEN 'MULTIPLE' THEN 43.0 WHEN 'BLANK' THEN 34.0
                     WHEN 'OX' THEN 17.0 WHEN 'ESSAY' THEN 4.5 ELSE 1.5 END
       THEN 'PASS' ELSE 'FAIL' END AS status
FROM problem p JOIN problem_set ps ON ps.id = p.problem_set_id
WHERE ps.session_id LIKE 'seed-%'
GROUP BY ps.quiz_type;

-- ③ explanation_content 평균 바이트 (lazy 컬럼·데모 핵심) 실측 대비 ±10%
SELECT
  CONCAT('explsize_', ps.quiz_type) AS check_name,
  ROUND(AVG(LENGTH(p.explanation_content))) AS actual_bytes,
  CASE ps.quiz_type WHEN 'MULTIPLE' THEN 2396 WHEN 'BLANK' THEN 1719
       WHEN 'OX' THEN 1127 WHEN 'ESSAY' THEN 1703 ELSE 1709 END AS expected_bytes,
  CASE WHEN ABS(AVG(LENGTH(p.explanation_content))
              - CASE ps.quiz_type WHEN 'MULTIPLE' THEN 2396 WHEN 'BLANK' THEN 1719
                     WHEN 'OX' THEN 1127 WHEN 'ESSAY' THEN 1703 ELSE 1709 END)
            <= 0.1 * CASE ps.quiz_type WHEN 'MULTIPLE' THEN 2396 WHEN 'BLANK' THEN 1719
                     WHEN 'OX' THEN 1127 WHEN 'ESSAY' THEN 1703 ELSE 1709 END
       THEN 'PASS' ELSE 'FAIL' END AS status
FROM problem p JOIN problem_set ps ON ps.id = p.problem_set_id
WHERE ps.session_id LIKE 'seed-%'
GROUP BY ps.quiz_type;
