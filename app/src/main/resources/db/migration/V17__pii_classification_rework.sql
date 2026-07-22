-- pii_classification 정비 (프로덕션 V16 기준 첫 신규 마이그레이션 — 관련 변경을 V17 하나로 압축).
--   (1) 폴더 기능(V16) 신규 컬럼 분류 — V14 시드가 폴더 이전이라 누락됐던 것.
--   (2) 레거시 update_log 제거 — V1 생성, V2 에서 board(category='UPDATE_LOG')로 이관·이후 미사용, 매핑 엔티티 없음.
--   (3) 비식별화 + 이원화: 마스킹을 "재식별 키 = HASH / 나머지 = SAFE" 둘로 수렴.
--       근거: 방침 명시 수집 목적 = "서비스 품질 개선·운영(성능 분석)". 그 목적엔 원본 realism 이 필요하다.
--       콘텐츠(REDACT/DROP)는 SAFE 로, 실명 가능 nickname(FAKE)도 HASH 로 합쳐 → 재식별 키만 결정적 해시.
--       결과: 원본 realism 확보 + "누가"는 특정 불가(가명처리), 실사용 전략은 HASH·SAFE 이원화.
--       (enum 자체는 5값 유지 — 미분류 컬럼의 deny-by-default REDACT 안전망은 코드 레벨로 남겨둔다.)
-- ── (1) 폴더 컬럼 분류 (식별자만 HASH, 나머지 SAFE) ──
INSERT INTO pii_classification (table_name, column_name, strategy, note)
VALUES ('quiz_folder', 'id', 'SAFE', '대체키(surrogate PK)'),
       ('quiz_folder', 'user_id', 'HASH', 'FK→user (재식별 키 — 동일 HASH로 참조 정합)'),
       ('quiz_folder', 'name', 'SAFE', '폴더명(비식별 후 신원 무관)'),
       ('quiz_folder', 'created_at', 'SAFE', NULL),
       ('quiz_history', 'folder_id', 'SAFE', 'FK→quiz_folder')
ON DUPLICATE KEY UPDATE strategy = VALUES(strategy), note = VALUES(note);

-- ── (2) 레거시 update_log 제거 ──
DELETE FROM pii_classification WHERE table_name = 'update_log';
DROP TABLE IF EXISTS update_log;

-- ── (3) 이원화: 콘텐츠(REDACT/DROP) → SAFE, 실명 가능 nickname(FAKE) → HASH. 결과 = HASH/SAFE 둘. ──
UPDATE pii_classification SET strategy = 'SAFE' WHERE strategy IN ('REDACT', 'DROP');
UPDATE pii_classification SET strategy = 'HASH' WHERE strategy = 'FAKE';
