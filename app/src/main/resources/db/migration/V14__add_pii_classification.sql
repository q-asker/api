-- V14: PII 분류 레지스트리 (기존 엔티티 코드 무수정, 마스킹 정책 연결)
-- masked-export 가 이 테이블 + information_schema 를 조인해 컬럼별 마스킹을 자동 적용한다.
-- strategy: HASH(결정적 해시, 식별자·FK) / FAKE(가짜값) / REDACT(원문 제거) / DROP(값 제거) / SAFE(원본 유지)
-- deny-by-default: 이 표에 없는 컬럼은 masked-export 가 기본 마스킹한다. SAFE 로 명시된 것만 원본 유지.
CREATE TABLE pii_classification
(
    table_name  VARCHAR(64)                                 NOT NULL,
    column_name VARCHAR(64)                                 NOT NULL,
    strategy    ENUM ('HASH','FAKE','REDACT','DROP','SAFE') NOT NULL,
    note        VARCHAR(255)                                NULL,
    PRIMARY KEY (table_name, column_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 식별자·FK: 결정적 HASH (같은 값→같은 해시 → 참조무결성 유지)
INSERT INTO pii_classification (table_name, column_name, strategy, note)
VALUES ('user', 'user_id', 'HASH', 'OAuth 식별자'),
       ('problem_set', 'user_id', 'HASH', 'FK→user'),
       ('quiz_history', 'user_id', 'HASH', 'FK→user'),
       ('essay_grade_log', 'user_id', 'HASH', 'FK→user'),
       ('refresh_token', 'user_id', 'HASH', 'FK→user(PK)'),
       ('board', 'user_id', 'HASH', '작성자'),
       ('feedback_board', 'user_id', 'HASH', '작성자'),
       ('reply', 'admin_id', 'HASH', '작성자'),
       ('problem_set', 'session_id', 'HASH', '세션 식별자(UNIQUE)'),
       ('refresh_token', 'rt_hash', 'HASH', '리프레시 토큰 해시(비밀)');

-- 표시명: 가짜값
INSERT INTO pii_classification (table_name, column_name, strategy, note)
VALUES ('user', 'nickname', 'FAKE', '표시 닉네임');

-- 사용자 생성 자유텍스트/입력: 원문 제거(REDACT)
INSERT INTO pii_classification (table_name, column_name, strategy, note)
VALUES ('board', 'title', 'REDACT', '게시글 제목'),
       ('board', 'content', 'REDACT', '게시글 본문'),
       ('feedback_board', 'content', 'REDACT', '피드백 본문'),
       ('reply', 'content', 'REDACT', '댓글 본문'),
       ('quiz_history', 'title', 'REDACT', '사용자 표시 제목'),
       ('quiz_history', 'answers', 'REDACT', '사용자 제출 답안(JSON)'),
       ('essay_grade_log', 'question', 'REDACT', '문항 텍스트'),
       ('essay_grade_log', 'student_answer', 'REDACT', '학생 답안'),
       ('essay_grade_log', 'overall_feedback', 'REDACT', '채점 피드백'),
       ('essay_grade_log', 'element_scores', 'REDACT', '요소 점수(JSON)'),
       ('essay_grade_log', 'evidence_json', 'REDACT', '근거(JSON, 답안 인용 가능)'),
       ('problem_set', 'title', 'REDACT', '업로드 문서명 유래 가능'),
       ('problem_set', 'file_url', 'REDACT', '업로드 파일 URL(파일명 노출 가능)'),
       ('problem_set', 'custom_instruction', 'REDACT', '사용자 입력 지시');

-- 비민감(구조·타임스탬프·수치·열거·AI 생성 콘텐츠·인증 메타): 원본 유지
INSERT INTO pii_classification (table_name, column_name, strategy, note)
VALUES ('user', 'created_at', 'SAFE', NULL),
       ('user', 'provider', 'SAFE', NULL),
       ('user', 'role', 'SAFE', NULL),
       ('problem_set', 'id', 'SAFE', NULL),
       ('problem_set', 'created_at', 'SAFE', NULL),
       ('problem_set', 'total_quiz_count', 'SAFE', NULL),
       ('problem_set', 'generation_status', 'SAFE', NULL),
       ('problem_set', 'quiz_type', 'SAFE', NULL),
       ('problem', 'number', 'SAFE', NULL),
       ('problem', 'problem_set_id', 'SAFE', NULL),
       ('problem', 'title', 'SAFE', 'AI 생성'),
       ('problem', 'created_at', 'SAFE', NULL),
       ('problem', 'explanation_content', 'SAFE', 'AI 생성'),
       ('problem', 'referenced_pages', 'SAFE', NULL),
       ('problem', 'applied_instruction', 'SAFE', 'AI 생성'),
       ('problem', 'selections', 'SAFE', 'AI 생성'),
       ('quiz_history', 'id', 'SAFE', NULL),
       ('quiz_history', 'problem_set_id', 'SAFE', NULL),
       ('quiz_history', 'score', 'SAFE', NULL),
       ('quiz_history', 'total_time', 'SAFE', NULL),
       ('quiz_history', 'created_at', 'SAFE', NULL),
       ('quiz_history', 'status', 'SAFE', NULL),
       ('essay_grade_log', 'id', 'SAFE', NULL),
       ('essay_grade_log', 'problem_set_id', 'SAFE', NULL),
       ('essay_grade_log', 'problem_number', 'SAFE', NULL),
       ('essay_grade_log', 'attempt_count', 'SAFE', NULL),
       ('essay_grade_log', 'total_score', 'SAFE', NULL),
       ('essay_grade_log', 'max_score', 'SAFE', NULL),
       ('essay_grade_log', 'created_at', 'SAFE', NULL),
       ('refresh_token', 'created_at', 'SAFE', NULL),
       ('refresh_token', 'expires_at', 'SAFE', NULL),
       ('board', 'board_id', 'SAFE', NULL),
       ('board', 'created_at', 'SAFE', NULL),
       ('board', 'updated_at', 'SAFE', NULL),
       ('board', 'view_count', 'SAFE', NULL),
       ('board', 'status', 'SAFE', NULL),
       ('board', 'category', 'SAFE', NULL),
       ('feedback_board', 'feedback_board_id', 'SAFE', NULL),
       ('feedback_board', 'created_at', 'SAFE', NULL),
       ('reply', 'reply_id', 'SAFE', NULL),
       ('reply', 'board_id', 'SAFE', NULL),
       ('reply', 'created_at', 'SAFE', NULL),
       ('problem_quality_log', 'id', 'SAFE', NULL),
       ('problem_quality_log', 'problem_set_id', 'SAFE', NULL),
       ('problem_quality_log', 'number', 'SAFE', NULL),
       ('problem_quality_log', 'v1_question_json', 'SAFE', 'AI 생성'),
       ('problem_quality_log', 'v1_explanation', 'SAFE', 'AI 생성'),
       ('problem_quality_log', 'v1_feedback', 'SAFE', 'AI 생성'),
       ('problem_quality_log', 'v2_question_json', 'SAFE', 'AI 생성'),
       ('problem_quality_log', 'v2_explanation', 'SAFE', 'AI 생성'),
       ('problem_quality_log', 'v2_feedback', 'SAFE', 'AI 생성'),
       ('problem_quality_log', 'review', 'SAFE', 'AI 생성'),
       ('problem_quality_log', 'created_at', 'SAFE', NULL);
