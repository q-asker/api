-- 방치 세트 정리 스케줄러(purgeStaleProblemSets) 측정용 시드. problem_set 의 status 분포를 실운영에 가깝게 조성한다.
-- 매 레벨 부하 직전 재적용(run.sh run_level) → 레벨마다 동일 분포. 전부 id 순서 기반 결정적(무작위 아님)·완전 idempotent.
--
-- 개수 유도 (실측 지표 기반, 스케일 무관 고정):
--   최대 4 RPM → 100배 성장 가정 400 RPM · 실패율 6%(메트릭 실측) · 스케줄러 주기 1분.
--   → 스테일 FAILED(삭제 대상) = 분당 실패 400×0.06 = 24건. 스케줄러가 매 주기 이만큼 지운다.
--
-- 왜 비율(%)이 아니라 고정 개수인가: 스테일은 스케줄러가 매 주기 삭제하므로 정상상태 개수는 (실패율 × 주기)로
--   상한이 잡히고 총 DB 크기와 무관하다. 비율로 찍으면 x100 에서 스테일이 수만 개 → child delete 가 락 타임아웃.
--   ORDER BY id LIMIT 로 고정 개수만 찍는다(최저 id 우선 — copy 오프셋 밖 base 행이라 스케일 전반에서 동일 원본).

-- ── Step 0: 전체 COMPLETED 로 리셋(직전 시딩 잔재 제거) ──
UPDATE problem_set SET generation_status = 'COMPLETED';

-- ── Step 1: 스테일 FAILED 24 (삭제 대상) — 최저 id 24개, 2시간 전(항상 스테일) ──
UPDATE problem_set
SET generation_status = 'FAILED', created_at = NOW() - INTERVAL 2 HOUR
ORDER BY id LIMIT 24;
