-- V21: 오답 모아풀기 — 세트 출처 구분, 재출제 혈통, 풀이 완료 시각
--
-- problem_set.origin        자료 기반(DOCUMENT) vs 오답 모음(WRONG_ANSWER) 구분.
--                           목록에서 두 종류를 구별하고, 원본 자료를 전제로 하는 후속 동작을 오답 문제집에서 감추는 근거다.
-- problem_set.source_folder_id  어느 폴더에서 모았는지.
-- problem.origin_*          재출제된 문항이 가리키는 최초 조상(세트 id + 문항 번호). 복제할 때 조상 값을 물려받으므로
--                           세대가 반복돼도 항상 최초 문항을 가리킨다. 같은 문항이 두 번 담기지 않게 하는 중복 제거 키다.
-- quiz_history.completed_at 풀이를 마친 시각. created_at 은 기록 행이 처음 만들어진 시각이라 재풀이해도 갱신되지 않아
--                           "가장 최근에 틀린 순"을 표현할 수 없다. 이 정렬이 상한에서 어떤 문항이 남는지를 결정한다.
--                           기존 완료 행은 created_at 으로 백필해 표시 변화를 만들지 않는다.

ALTER TABLE problem_set
    ADD COLUMN origin           VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT',
    ADD COLUMN source_folder_id BIGINT NULL;

ALTER TABLE problem
    ADD COLUMN origin_problem_set_id BIGINT NULL,
    ADD COLUMN origin_number         INT NULL;

ALTER TABLE quiz_history
    ADD COLUMN completed_at DATETIME(6) NULL;

UPDATE quiz_history
SET completed_at = created_at
WHERE status = 'COMPLETED';

-- PII 분류(새 컬럼 커버리지 게이트) — 출처 구분·혈통 참조·완료 시각은 개인정보 아님.
INSERT INTO pii_classification (table_name, column_name, strategy, note)
VALUES ('problem_set', 'origin', 'SAFE', '세트 출처(DOCUMENT/WRONG_ANSWER)'),
       ('problem_set', 'source_folder_id', 'SAFE', 'FK→quiz_folder (수집 범위)'),
       ('problem', 'origin_problem_set_id', 'SAFE', '재출제 혈통(최초 조상 세트)'),
       ('problem', 'origin_number', 'SAFE', '재출제 혈통(최초 조상 문항번호)'),
       ('quiz_history', 'completed_at', 'SAFE', '풀이 완료 시각')
ON DUPLICATE KEY UPDATE strategy = VALUES(strategy), note = VALUES(note);
