-- 오답 모아풀기 기능 E2E 시드.
--
-- 한 폴더에 여러 유형의 문제집과 "일부만 틀린" 풀이 기록을 만들어, 유형별 분할·서술형 제외·범위 격리를
-- 한 번의 실행으로 관측할 수 있게 한다. 마지막 두 세트는 함정이다 — 폴더 밖 기록과 타인 기록이며,
-- 수집 범위가 새면 객관식 문항 수가 3이 아니라 7로 나온다.
--
-- 실행 결과 기대값: 객관식 3문제 / OX 2문제 / 빈칸 직접입력 2문제 세 개가 만들어지고, 서술형 3문항은 제외된다.
--
-- 사용자 로컬 DB와 격리된 일회용 DB에서 돌린다(api/CLAUDE.local.md "기능 E2E를 사용자 DB와 격리해 돌리는 법").
--   docker exec -i q-asker-db mysql --default-character-set=utf8mb4 \
--     -uuser -p<pw> <일회용DB> < scripts/e2e/seed-wrong-answer-set.sql
--
-- 지우고 다시 넣으므로 몇 번을 실행해도 같은 상태가 된다. id 를 90만 대로 고정했으므로 어느 DB 에 넣어도
-- 인코딩된 식별자가 같다 — 프론트 스펙이 그 값을 상수로 들고 있어도 깨지지 않는다.

SET NAMES utf8mb4;

-- 시드 세트뿐 아니라 지난 실행에서 만들어진 오답 문제집까지 소유자 기준으로 걷어낸다(만들어지는 id 를 미리 알 수 없다).
DELETE FROM problem WHERE problem_set_id IN
  (SELECT id FROM problem_set WHERE user_id IN ('e2e-008-user', 'e2e-008-other'));
DELETE FROM quiz_history WHERE user_id IN ('e2e-008-user', 'e2e-008-other');
DELETE FROM problem_set WHERE user_id IN ('e2e-008-user', 'e2e-008-other');
DELETE FROM quiz_folder WHERE user_id IN ('e2e-008-user', 'e2e-008-other');
DELETE FROM user WHERE user_id IN ('e2e-008-user', 'e2e-008-other');

INSERT INTO user (user_id, role, provider, nickname, created_at)
VALUES ('e2e-008-user', 'ROLE_USER', 'GOOGLE', 'E2E 오답유저', NOW(6)),
       ('e2e-008-other', 'ROLE_USER', 'GOOGLE', 'E2E 타인', NOW(6));

INSERT INTO quiz_folder (id, user_id, name, created_at)
VALUES (900001, 'e2e-008-user', 'E2E-WRONG-ANSWER', NOW(6)),
       (900002, 'e2e-008-user', 'E2E-OTHER-FOLDER', NOW(6));


INSERT INTO problem_set (id, title, user_id, generation_status, quiz_type, total_quiz_count, session_id, file_url, origin, created_at) VALUES
  (900001, 'E2E 객관식 원본', 'e2e-008-user', 'COMPLETED', 'MULTIPLE', 5, 'e2e-008-900001', 'https://example.invalid/e2e.pdf', 'DOCUMENT', NOW(6)),
  (900002, 'E2E OX 원본', 'e2e-008-user', 'COMPLETED', 'OX', 5, 'e2e-008-900002', 'https://example.invalid/e2e.pdf', 'DOCUMENT', NOW(6)),
  (900003, 'E2E 빈칸 직접입력 원본', 'e2e-008-user', 'COMPLETED', 'REAL_BLANK', 3, 'e2e-008-900003', 'https://example.invalid/e2e.pdf', 'DOCUMENT', NOW(6)),
  (900004, 'E2E 서술형 원본', 'e2e-008-user', 'COMPLETED', 'ESSAY', 3, 'e2e-008-900004', 'https://example.invalid/e2e.pdf', 'DOCUMENT', NOW(6)),
  (900005, 'E2E 폴더 밖 객관식(함정)', 'e2e-008-user', 'COMPLETED', 'MULTIPLE', 5, 'e2e-008-900005', 'https://example.invalid/e2e.pdf', 'DOCUMENT', NOW(6)),
  (900006, 'E2E 타인 소유 객관식(함정)', 'e2e-008-other', 'COMPLETED', 'MULTIPLE', 5, 'e2e-008-900006', 'https://example.invalid/e2e.pdf', 'DOCUMENT', NOW(6));


INSERT INTO problem (problem_set_id, number, title, selections, explanation_content, referenced_pages, created_at) VALUES
  (900001, 1, '900001-1번 문항 지문', '[{"content": "1번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "1번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "1번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "1번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '1번 해설', '[1, 2]', NOW(6)),
  (900001, 2, '900001-2번 문항 지문', '[{"content": "2번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "2번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "2번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "2번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '2번 해설', '[1, 2]', NOW(6)),
  (900001, 3, '900001-3번 문항 지문', '[{"content": "3번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "3번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "3번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "3번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '3번 해설', '[1, 2]', NOW(6)),
  (900001, 4, '900001-4번 문항 지문', '[{"content": "4번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "4번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "4번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "4번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '4번 해설', '[1, 2]', NOW(6)),
  (900001, 5, '900001-5번 문항 지문', '[{"content": "5번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "5번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "5번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "5번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '5번 해설', '[1, 2]', NOW(6)),
  (900002, 1, '900002-1번 문항 지문', '[{"content": "O", "explanation": "틀린 설명", "correct": false, "acceptedAnswers": null}, {"content": "X", "explanation": "맞는 설명", "correct": true, "acceptedAnswers": null}]', '1번 해설', '[1, 2]', NOW(6)),
  (900002, 2, '900002-2번 문항 지문', '[{"content": "O", "explanation": "틀린 설명", "correct": false, "acceptedAnswers": null}, {"content": "X", "explanation": "맞는 설명", "correct": true, "acceptedAnswers": null}]', '2번 해설', '[1, 2]', NOW(6)),
  (900002, 3, '900002-3번 문항 지문', '[{"content": "O", "explanation": "틀린 설명", "correct": false, "acceptedAnswers": null}, {"content": "X", "explanation": "맞는 설명", "correct": true, "acceptedAnswers": null}]', '3번 해설', '[1, 2]', NOW(6)),
  (900002, 4, '900002-4번 문항 지문', '[{"content": "O", "explanation": "틀린 설명", "correct": false, "acceptedAnswers": null}, {"content": "X", "explanation": "맞는 설명", "correct": true, "acceptedAnswers": null}]', '4번 해설', '[1, 2]', NOW(6)),
  (900002, 5, '900002-5번 문항 지문', '[{"content": "O", "explanation": "틀린 설명", "correct": false, "acceptedAnswers": null}, {"content": "X", "explanation": "맞는 설명", "correct": true, "acceptedAnswers": null}]', '5번 해설', '[1, 2]', NOW(6)),
  (900003, 1, '900003-1번 문항 지문', '[{"content": "정답1", "explanation": "빈칸 해설", "correct": true, "acceptedAnswers": [["정답1", "answer1"]]}]', '1번 해설', '[1, 2]', NOW(6)),
  (900003, 2, '900003-2번 문항 지문', '[{"content": "정답2", "explanation": "빈칸 해설", "correct": true, "acceptedAnswers": [["정답2", "answer2"]]}]', '2번 해설', '[1, 2]', NOW(6)),
  (900003, 3, '900003-3번 문항 지문', '[{"content": "정답3", "explanation": "빈칸 해설", "correct": true, "acceptedAnswers": [["정답3", "answer3"]]}]', '3번 해설', '[1, 2]', NOW(6)),
  (900004, 1, '900004-1번 문항 지문', '[{"content": "1번 모범답안", "explanation": "채점 기준", "correct": true, "acceptedAnswers": null}]', '1번 해설', '[1, 2]', NOW(6)),
  (900004, 2, '900004-2번 문항 지문', '[{"content": "2번 모범답안", "explanation": "채점 기준", "correct": true, "acceptedAnswers": null}]', '2번 해설', '[1, 2]', NOW(6)),
  (900004, 3, '900004-3번 문항 지문', '[{"content": "3번 모범답안", "explanation": "채점 기준", "correct": true, "acceptedAnswers": null}]', '3번 해설', '[1, 2]', NOW(6)),
  (900005, 1, '900005-1번 문항 지문', '[{"content": "1번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "1번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "1번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "1번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '1번 해설', '[1, 2]', NOW(6)),
  (900005, 2, '900005-2번 문항 지문', '[{"content": "2번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "2번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "2번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "2번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '2번 해설', '[1, 2]', NOW(6)),
  (900005, 3, '900005-3번 문항 지문', '[{"content": "3번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "3번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "3번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "3번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '3번 해설', '[1, 2]', NOW(6)),
  (900005, 4, '900005-4번 문항 지문', '[{"content": "4번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "4번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "4번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "4번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '4번 해설', '[1, 2]', NOW(6)),
  (900005, 5, '900005-5번 문항 지문', '[{"content": "5번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "5번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "5번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "5번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '5번 해설', '[1, 2]', NOW(6)),
  (900006, 1, '900006-1번 문항 지문', '[{"content": "1번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "1번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "1번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "1번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '1번 해설', '[1, 2]', NOW(6)),
  (900006, 2, '900006-2번 문항 지문', '[{"content": "2번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "2번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "2번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "2번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '2번 해설', '[1, 2]', NOW(6)),
  (900006, 3, '900006-3번 문항 지문', '[{"content": "3번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "3번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "3번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "3번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '3번 해설', '[1, 2]', NOW(6)),
  (900006, 4, '900006-4번 문항 지문', '[{"content": "4번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "4번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "4번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "4번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '4번 해설', '[1, 2]', NOW(6)),
  (900006, 5, '900006-5번 문항 지문', '[{"content": "5번 오답 보기", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "5번 정답 보기", "explanation": "정답 사유", "correct": true, "acceptedAnswers": null}, {"content": "5번 오답 보기2", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}, {"content": "5번 오답 보기3", "explanation": "오답 사유", "correct": false, "acceptedAnswers": null}]', '5번 해설', '[1, 2]', NOW(6));


-- 풀이 기록. completed_at 을 서로 다르게 둬 "가장 최근에 틀린 순" 정렬이 결정적으로 재현되게 한다.
INSERT INTO quiz_history (id, user_id, problem_set_id, folder_id, title, answers, score, completed_at, total_time, status, created_at) VALUES
  (900001, 'e2e-008-user', 900001, 900001, 'E2E 객관식 원본 풀이', '[{"number": 1, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 2, "userAnswer": 2, "inReview": false, "textAnswer": null}, {"number": 3, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 4, "userAnswer": 2, "inReview": false, "textAnswer": null}, {"number": 5, "userAnswer": 1, "inReview": false, "textAnswer": null}]', 2, DATE_SUB(NOW(6), INTERVAL 6 MINUTE), '00:05:00', 'COMPLETED', NOW(6)),
  (900002, 'e2e-008-user', 900002, 900001, 'E2E OX 원본 풀이', '[{"number": 1, "userAnswer": 2, "inReview": false, "textAnswer": null}, {"number": 2, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 3, "userAnswer": 2, "inReview": false, "textAnswer": null}, {"number": 4, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 5, "userAnswer": 2, "inReview": false, "textAnswer": null}]', 3, DATE_SUB(NOW(6), INTERVAL 5 MINUTE), '00:05:00', 'COMPLETED', NOW(6)),
  (900003, 'e2e-008-user', 900003, 900001, 'E2E 빈칸 직접입력 원본 풀이', '[{"number": 1, "userAnswer": 0, "inReview": false, "textAnswer": "틀린답"}, {"number": 2, "userAnswer": 0, "inReview": false, "textAnswer": "틀린답"}, {"number": 3, "userAnswer": 0, "inReview": false, "textAnswer": "정답3"}]', 1, DATE_SUB(NOW(6), INTERVAL 4 MINUTE), '00:05:00', 'COMPLETED', NOW(6)),
  (900004, 'e2e-008-user', 900004, 900001, 'E2E 서술형 원본 풀이', '[{"number": 1, "userAnswer": 0, "inReview": false, "textAnswer": "1번 서술형 답안"}, {"number": 2, "userAnswer": 0, "inReview": false, "textAnswer": "2번 서술형 답안"}, {"number": 3, "userAnswer": 0, "inReview": false, "textAnswer": "3번 서술형 답안"}]', 3, DATE_SUB(NOW(6), INTERVAL 3 MINUTE), '00:05:00', 'COMPLETED', NOW(6)),
  (900005, 'e2e-008-user', 900005, 900002, 'E2E 폴더 밖 객관식(함정) 풀이', '[{"number": 1, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 2, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 3, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 4, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 5, "userAnswer": 2, "inReview": false, "textAnswer": null}]', 1, DATE_SUB(NOW(6), INTERVAL 2 MINUTE), '00:05:00', 'COMPLETED', NOW(6)),
  (900006, 'e2e-008-other', 900006, 900001, 'E2E 타인 소유 객관식(함정) 풀이', '[{"number": 1, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 2, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 3, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 4, "userAnswer": 1, "inReview": false, "textAnswer": null}, {"number": 5, "userAnswer": 2, "inReview": false, "textAnswer": null}]', 1, DATE_SUB(NOW(6), INTERVAL 1 MINUTE), '00:05:00', 'COMPLETED', NOW(6));
