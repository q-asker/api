-- 1) refresh_token: 토큰 회전 FOR UPDATE 풀스캔 -> 단일 행 락으로 축소.
--    rt_hash 는 값이 유일(회전마다 새 해시)하므로 UNIQUE.
CREATE UNIQUE INDEX uq_refresh_token_rt_hash ON refresh_token (rt_hash);

-- 2) problem_set: 스테일 정리 스케줄러 findByGenerationStatusInAndCreatedAtBefore 풀스캔 -> 범위 스캔.
CREATE INDEX idx_problem_set_status_created ON problem_set (generation_status, created_at);

-- 3) board: 목록 최신순 정렬(ORDER BY created_at DESC LIMIT)을 인덱스로 대체(filesort 제거).
CREATE INDEX idx_board_category_created_at ON board (category, created_at, status);
