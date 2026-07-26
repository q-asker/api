-- E2E 시드: REAL_BLANK 관용 채점(feedback #20) 실플로우 검증용 (throwaway DB `qasker_e2e`).
--
-- 적용 순서:
--   (1) 사용자가 빈 DB 생성:  CREATE DATABASE qasker_e2e CHARACTER SET utf8mb4;
--   (2) 백엔드 기동(SPRING_DATASOURCE_URL=.../qasker_e2e)으로 Flyway V1~V17 자동 마이그레이션 → 스키마 생성.
--   (3) 이 파일 적용:  mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u <계정> -p qasker_e2e < 이 파일
--
-- 재적용 안전(idempotent): 기존 시드 행을 지우고 다시 넣는다. 시드 problem_set.id=9001 은 고정.
-- 응답의 problemSetId 는 Hashids 로 인코딩되므로(백엔드 hashUtil.encode), 프론트에 줄 값은 기동 후 API 로 확인한다.

DELETE FROM problem WHERE problem_set_id = 9001;
DELETE FROM problem_set WHERE id = 9001;
DELETE FROM user WHERE user_id = 'e2e-seed-user';

-- 시드 유저 (/local/token?userId=e2e-seed-user 로 토큰 발급). user_id 는 수동 PK.
INSERT INTO user (user_id, role, provider, nickname, created_at)
VALUES ('e2e-seed-user', 'ROLE_USER', 'local', 'E2E Seed User', NOW(6));

-- REAL_BLANK 세트 (완료 상태). id 고정 9001.
INSERT INTO problem_set
  (id, title, user_id, generation_status, quiz_type, total_quiz_count, session_id, file_url, created_at)
VALUES
  (9001, 'E2E 관용채점 시드셋', 'e2e-seed-user', 'COMPLETED', 'REAL_BLANK', 1,
   'e2e-seed-session-blank-001', '', NOW(6));

-- Q1: 단일 빈칸 REAL_BLANK. 정답 "동위원소" + 허용변형(아이소토프/isotope) + 오답선지 3개.
--   selections: 정답(correct=true) + 오답선지(correct=false, D-guard 즉시 오답 검증용)
--   accepted_answers: 빈칸별 {answer, accepted[]}. "isotope"=영문 이표기 인정, "동소체"=오답선지→오답 유지.
INSERT INTO problem
  (problem_set_id, number, title, selections, accepted_answers, explanation_content, referenced_pages, created_at)
VALUES
  (9001, 1,
   '**원자 구조**에서 화학적 성질은 동일하지만 질량이 서로 다른 _______은(는) 원자핵 내 중성자 수의 차이에서 비롯되며, 일부는 방사성 붕괴를 일으킨다.',
   '[{"content":"동위원소","explanation":"정답","correct":true},{"content":"동소체","explanation":"유사개념형 오답","correct":false},{"content":"이성질체","explanation":"유사개념형 오답","correct":false},{"content":"동족원소","explanation":"혼동유발형 오답","correct":false}]',
   '[{"answer":"동위원소","accepted":["아이소토프","isotope"]}]',
   '동위원소는 양성자 수는 같고 중성자 수가 달라 질량수가 다른 원소다. 동소체·이성질체·동족원소는 뜻이 다른 별개 개념이다.',
   '[1]',
   NOW(6));
