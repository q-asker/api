ALTER TABLE problem_set
    ADD COLUMN file_url VARCHAR(512) NOT NULL DEFAULT '' AFTER session_id;
