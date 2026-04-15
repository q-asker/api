-- quiz_history.answers 컬럼을 TEXT → JSON으로 변경
-- 기존 테이블이 Flyway 도입 전에 TEXT로 생성되어 V1 baseline의 JSON 정의가 적용되지 않았음
ALTER TABLE quiz_history MODIFY COLUMN answers JSON;
