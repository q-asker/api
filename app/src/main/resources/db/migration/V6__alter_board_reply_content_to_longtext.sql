-- board.content, reply.content 컬럼을 LONGTEXT로 변경
ALTER TABLE board MODIFY COLUMN content LONGTEXT NOT NULL;
ALTER TABLE reply MODIFY COLUMN content LONGTEXT NOT NULL;
