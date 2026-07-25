-- =============================================================
-- seed-x1-base.sql — x1 베이스에서 FLOOR 미달 소형 테이블을 @target(기본100) 행으로 "맞춘다"(부족분만 합성).
--   대상: board·feedback_board·quiz_folder·reply. 기존 user·board 를 FK 로 재사용해 채운다.
--   목적: x1 base 를 100 으로 올려두면 seed-scale 의 ×scale 복제가 x100 에서 10,000(=100×100)을 만든다.
--   오프셋 @xbase 는 seed-scale 복제 필터(WHERE id < @base=1,000,000) '안'에 들도록 @base 미만 → 합성 행도 ×scale 복제됨.
--   재실행 안전: 부족분(@target - 현재)만 추가. 입력: @target(기본 100).
-- 전제: x1 원본이 이미 적재된 상태(user·board 가 FK 소스). x1 컨테이너에서 실행.
-- =============================================================
SET NAMES utf8mb4;
SET @target = COALESCE(@target, 100);
SET @xbase  = 500000;   -- x1 원본 id ≪ 500000 < @base(1,000,000): 원본 비충돌 + 복제 대상 포함

-- 0..999 숫자 생성기(필요분만 WHERE 로 컷; @target ≤ 1000 가정)
DROP TEMPORARY TABLE IF EXISTS nb;
CREATE TEMPORARY TABLE nb (n INT PRIMARY KEY);
INSERT INTO nb (n)
SELECT a.d + b.d*10 + c.d*100
FROM (SELECT 0 d UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
CROSS JOIN (SELECT 0 d UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
CROSS JOIN (SELECT 0 d UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c;

-- 유효 FK 소스: user 순번(라운드로빈)
DROP TEMPORARY TABLE IF EXISTS u_idx;
CREATE TEMPORARY TABLE u_idx (rn INT PRIMARY KEY, user_id VARCHAR(255));
INSERT INTO u_idx (rn, user_id)
SELECT (ROW_NUMBER() OVER (ORDER BY user_id)) - 1, user_id FROM user;
SET @U = (SELECT COUNT(*) FROM u_idx);

SET @tpl_status   = (SELECT status   FROM board LIMIT 1);
SET @tpl_category = (SELECT category FROM board LIMIT 1);

-- ── board (user_id FK, 템플릿 status·category) — 부족분만 ──
SET @need_b = (SELECT GREATEST(0, @target - COUNT(*)) FROM board);
INSERT INTO board (board_id, created_at, updated_at, view_count, title, user_id, content, status, category)
SELECT @xbase + n.n, NOW(6), NOW(6), 0, CONCAT('[base] board ', n.n), ui.user_id,
       CONCAT('[base] content ', n.n), @tpl_status, @tpl_category
FROM nb n JOIN u_idx ui ON ui.rn = n.n MOD @U
WHERE n.n < @need_b;

-- board 를 채운 뒤 board 순번 재구성(reply FK 소스)
DROP TEMPORARY TABLE IF EXISTS b_idx;
CREATE TEMPORARY TABLE b_idx (rn INT PRIMARY KEY, board_id BIGINT);
INSERT INTO b_idx (rn, board_id)
SELECT (ROW_NUMBER() OVER (ORDER BY board_id)) - 1, board_id FROM board;
SET @B = (SELECT COUNT(*) FROM b_idx);

-- ── feedback_board (user_id FK) — 부족분만 ──
SET @need_f = (SELECT GREATEST(0, @target - COUNT(*)) FROM feedback_board);
INSERT INTO feedback_board (feedback_board_id, user_id, content, created_at)
SELECT @xbase + n.n, ui.user_id, CONCAT('[base] feedback ', n.n), NOW(6)
FROM nb n JOIN u_idx ui ON ui.rn = n.n MOD @U
WHERE n.n < @need_f;

-- ── quiz_folder (user_id FK) — 부족분만 ──
SET @need_q = (SELECT GREATEST(0, @target - COUNT(*)) FROM quiz_folder);
INSERT INTO quiz_folder (id, user_id, name, created_at)
SELECT @xbase + n.n, ui.user_id, CONCAT('base_folder_', n.n), NOW(6)
FROM nb n JOIN u_idx ui ON ui.rn = n.n MOD @U
WHERE n.n < @need_q;

-- ── reply (board_id FK, admin_id FK=user) — 원본 0 → 전량, 부족분만 ──
SET @need_r = (SELECT GREATEST(0, @target - COUNT(*)) FROM reply);
INSERT INTO reply (reply_id, board_id, admin_id, content, created_at)
SELECT @xbase + n.n, bi.board_id, ui.user_id, CONCAT('[base] reply ', n.n), NOW(6)
FROM nb n
JOIN b_idx bi ON bi.rn = n.n MOD @B
JOIN u_idx ui ON ui.rn = n.n MOD @U
WHERE n.n < @need_r;

DROP TEMPORARY TABLE IF EXISTS nb;
DROP TEMPORARY TABLE IF EXISTS u_idx;
DROP TEMPORARY TABLE IF EXISTS b_idx;
