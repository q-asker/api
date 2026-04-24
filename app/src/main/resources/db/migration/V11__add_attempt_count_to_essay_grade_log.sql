ALTER TABLE essay_grade_log
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 1 AFTER student_answer;
