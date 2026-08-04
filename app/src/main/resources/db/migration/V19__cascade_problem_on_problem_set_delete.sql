SET @drops = (SELECT GROUP_CONCAT(DISTINCT 'DROP FOREIGN KEY ', constraint_name SEPARATOR ', ')
              FROM information_schema.key_column_usage
              WHERE table_schema = DATABASE()
                AND table_name = 'problem'
                AND referenced_table_name = 'problem_set');
SET @sql = IF(@drops IS NULL, 'SELECT 1', CONCAT('ALTER TABLE problem ', @drops));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── ON DELETE CASCADE 로 단일 FK 재생성 ──
ALTER TABLE problem
    ADD CONSTRAINT fk_problem_problem_set
        FOREIGN KEY (problem_set_id) REFERENCES problem_set (id) ON DELETE CASCADE;
