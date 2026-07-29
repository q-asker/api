-- 동일 재현(이어풀기)용 생성 조건 보강: 세트가 pageNumbers·language를 소유해 조회 시 되짚을 수 있게 한다.
-- 기존 legacy 행은 두 값 NULL → 조회 응답에서 폴백(US2)으로 유도된다.
ALTER TABLE problem_set
    ADD COLUMN page_numbers TEXT NULL,
    ADD COLUMN language VARCHAR(8) NULL;

-- PII 분류(새 컬럼 커버리지 게이트) — 페이지 번호 목록·언어 코드는 개인정보 아님.
INSERT INTO pii_classification (table_name, column_name, strategy, note)
VALUES ('problem_set', 'page_numbers', 'SAFE', NULL),
       ('problem_set', 'language', 'SAFE', NULL);
