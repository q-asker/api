CREATE TABLE IF NOT EXISTS refresh_token (
    user_id VARCHAR(255) NOT NULL,
    rt_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_refresh_token_rt_hash (rt_hash)
);

SELECT COUNT(*)
INTO @expires_at_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'refresh_token'
  AND column_name = 'expires_at';

SET @expires_at_sql = IF(
    @expires_at_exists = 0,
    'ALTER TABLE refresh_token ADD COLUMN expires_at TIMESTAMP(6) NULL',
    'SELECT 1'
);

PREPARE stmt FROM @expires_at_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
