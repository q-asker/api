-- V2: board 테이블에 category 컬럼 추가 + update_log 데이터 이관

-- 1. board 테이블에 category 컬럼 추가
ALTER TABLE board ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'INQUIRY';

-- 2. update_log 데이터를 board로 마이그레이션
INSERT INTO board (user_id, title, content, view_count, status, category, created_at)
SELECT 'SYSTEM', update_text, update_text, 0, 'IN_PROGRESS', 'UPDATE_LOG', created_at
FROM update_log;
