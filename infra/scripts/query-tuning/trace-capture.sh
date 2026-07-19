#!/usr/bin/env bash
# traceId 캡처: history_long 활성 → 실 API 엔드포인트 부하(요청마다 distinct reqId, uri=실제 엔드포인트)
#   → reqId 태그 스냅샷 테이블. 대시보드가 trace_snapshot 조회.
# 전제: 앱이 3307에 local,loadtest,mock 프로파일로 떠 있어야 함
#   (mock: write 서비스가 자기정리 mock으로 교체돼 실 write 엔드포인트를 순증 0으로 태움 — 외부 AI·GitHub 호출 없음).
set -euo pipefail
CONTAINER="${CONTAINER:-local-mysql-prod}"
BASE="${BASE:-http://localhost:8080}"
USER_ID="${USER_ID:-h_e9887d1d5b31f89c3101b5732df92f4c}"
SWEEP="${SWEEP:-40}"       # 목록 GET 반복
WRITE="${WRITE:-10}"       # 각 write 엔드포인트 반복
REFRESH="${REFRESH:-150}"  # refresh 반복
MY(){ docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot "$@" 2>/dev/null; }

echo "[trace] history_long 활성 + 버퍼 리셋"
MY -e "UPDATE performance_schema.setup_consumers SET ENABLED='YES' WHERE NAME='events_statements_history_long';
       TRUNCATE performance_schema.events_statements_history_long;"
MY qaskerdb -e "DROP TABLE IF EXISTS trace_snapshot;"

TOKEN=$(curl -s "$BASE/local/token?userId=$USER_ID")
AUTH=(-H "Authorization: Bearer $TOKEN")
JSON=(-H "Content-Type: application/json")
# write 엔드포인트 호출 헬퍼(실패해도 트레이스는 계속 — 부작용은 mock이 자기정리로 흡수).
REQ(){ curl -s -o /dev/null --max-time 8 "${AUTH[@]}" "${JSON[@]}" "$@" || true; }

echo "[trace] 목록 GET(진짜 uri: GET:/history, GET:/boards) + hashid 수확"
IDPOOL=$(mktemp); trap 'rm -f "$IDPOOL"' EXIT
#   GET /boards 는 category 가 필수(@RequestParam BoardCategory) — 없으면 400 이라 board 가 트레이스에 안 잡힌다.
for r in $(seq 1 "$SWEEP"); do
  for ep in /history "/boards?category=INQUIRY"; do
    body=$(curl -s --max-time 8 "${AUTH[@]}" "$BASE$ep" || true)
    echo "$body" | jq -r '[.. | strings] | .[]' 2>/dev/null | grep -aE '^[A-Za-z0-9_-]{8,}$' >> "$IDPOOL" || true
  done
done
sort -u "$IDPOOL" -o "$IDPOOL"
# 수확 id 배열(board·problemSet·history 슬롯에 공용으로 재사용 — mock은 대부분 id를 무시하고 자기정리한다).
IDS=(); while IFS= read -r l; do [ -n "$l" ] && IDS+=("$l"); done < <(head -n 25 "$IDPOOL")
ID(){ [ ${#IDS[@]} -gt 0 ] && echo "${IDS[$(( $1 % ${#IDS[@]} ))]}" || echo "mock"; }

echo "[trace] 상세조회(실 uri: GET:/boards/{id} 등) — 수확 id 대입"
for tmpl in "/boards/%s" "/problem-set/%s" "/history/%s" "/history/%s/essay" "/history/check/%s" "/explanation/%s"; do
  for i in "${!IDS[@]}"; do
    curl -s -o /dev/null --max-time 8 "${AUTH[@]}" "$BASE$(printf "$tmpl" "${IDS[$i]}")" || true
  done
done

echo "[trace] 실 write 엔드포인트(mock 자기정리로 순증 0) — 각 실 uri 로 태깅"
for r in $(seq 1 "$WRITE"); do
  id=$(ID "$r")
  # board: POST/PUT/DELETE /boards
  REQ -X POST   "$BASE/boards"                    -d '{"title":"mock","content":"mock"}'
  REQ -X PUT    "$BASE/boards/$id"                -d '{"title":"mock","content":"mock"}'
  REQ -X DELETE "$BASE/boards/$id"
  # feedback: POST /feedback (GitHub 이슈·Slack 은 mock 이 생략)
  REQ -X POST   "$BASE/feedback"                  -d '{"content":"mock"}'
  # essay 채점: POST /essay/.../grade (문제조회는 실제, 채점은 mock, 로그 save→delete)
  REQ -X POST   "$BASE/essay/problem-sets/$id/problems/1/grade" -d '{"textAnswer":"mock","attemptCount":1}'
  # 닉네임 변경: PATCH /user/nickname (변경→원복)
  REQ -X PATCH  "$BASE/user/nickname"             -d '{"nickname":"mock"}'
  # 문제세트 제목: PATCH /problem-set/{id}/title (변경→원복, 소유자 아니면 403 이라도 read SQL 은 태워짐)
  REQ -X PATCH  "$BASE/problem-set/$id/title"     -d '{"title":"mock"}'
  # 히스토리: init/save(INSERT)·title(UPDATE)·delete(DELETE) — mock throwaway save→delete
  REQ -X POST   "$BASE/history/init"              -d "{\"problemSetId\":\"$id\",\"title\":\"mock\"}"
  REQ -X POST   "$BASE/history"                   -d "{\"problemSetId\":\"$id\",\"title\":\"mock\",\"userAnswers\":[],\"score\":0,\"totalTime\":\"0\"}"
  REQ -X PATCH  "$BASE/history/$id/title"         -d '{"title":"mock"}'
  REQ -X DELETE "$BASE/history/$id"
  REQ -X DELETE "$BASE/history/all"
  # 생성: POST /generation (동기 initProblemSet 만 태우고 롤백 — AI/비동기/SSE 없음)
  SID=$(uuidgen | tr 'A-Z' 'a-z')
  REQ -X POST   "$BASE/generation" -d "{\"sessionId\":\"$SID\",\"uploadedUrl\":\"mock\",\"title\":\"mock\",\"quizCount\":5,\"quizType\":\"MULTIPLE\",\"pageNumbers\":[1],\"language\":\"KO\"}"
  # 스케줄러 로직 온디맨드 — 비-controller 백그라운드 SELECT(findByGenerationStatusInAndCreatedAtBefore 스캔)를 실 uri로 태깅
  REQ -X POST   "$BASE/local/scheduler/stale-generation"
done

echo "[trace] refresh(실 uri: POST:/auth/refresh) — 요청마다 distinct reqId"
for r in $(seq 1 "$REFRESH"); do
  curl -s -o /dev/null --max-time 8 -X POST "$BASE/auth/refresh" -b "refresh_token=trace-$r" &
  [ $((r % 20)) -eq 0 ] && wait
done; wait

echo "[trace] 스냅샷 → qaskerdb.trace_snapshot (reqId 태그만)"
MY qaskerdb -e "
CREATE TABLE trace_snapshot AS
SELECT
  CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'reqId=',-1),' ',1) AS CHAR(16)) AS reqId,
  CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'uri=',-1),' ',1) AS CHAR(80))   AS uri,
  LEFT(TRIM(SUBSTRING_INDEX(SQL_TEXT,'*/',-1)),150)                              AS sql_stripped,
  -- repo·method는 앱이 심은 repoMethod= 주석에서 파싱한다(repoMethod=BoardRepository.save → repo=BoardRepository, method=save).
  --  단 레포 메서드를 안 거친 SQL(lazy-load 연관 초기화·dirty-check UPDATE 등 Hibernate가 자동 발생시킨 것)은 주석이 비므로,
  --  repo는 테이블명으로 복원해 대시보드 §② 레포 필터에 걸리게 하고, method는 'Hibernate query'로 표시해 레포 메서드와 구분한다.
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'repoMethod=',-1),' ',1),'.', 1),''),
    CASE
      -- from/update/into/delete 무관하게 테이블명 포함으로 매핑. 구체적(긴)·복합 이름을 먼저 둔다
      -- (feedback_board→board, problem_set→problem, *_id 컬럼만 있는 SQL이 user로 새지 않도록 user는 맨 뒤).
      WHEN SQL_TEXT LIKE '%refresh_token%'   THEN 'RefreshTokenRepository'
      WHEN SQL_TEXT LIKE '%quiz_history%'    THEN 'QuizHistoryRepository'
      WHEN SQL_TEXT LIKE '%essay_grade_log%' THEN 'EssayGradeLogRepository'
      WHEN SQL_TEXT LIKE '%feedback_board%'  THEN 'FeedbackBoardRepository'
      WHEN SQL_TEXT LIKE '%problem_set%'     THEN 'ProblemSetRepository'
      WHEN SQL_TEXT LIKE '%problem%'         THEN 'ProblemRepository'
      WHEN SQL_TEXT LIKE '%board%'           THEN 'BoardRepository'
      WHEN SQL_TEXT LIKE '%reply%'           THEN 'ReplyRepository'
      WHEN SQL_TEXT LIKE '%user%'            THEN 'UserRepository'
      ELSE '(기타)' END)                                                        AS repo,
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'repoMethod=',-1),' ',1),'.',-1),''),
    'Hibernate query')                                                          AS method,
  ROUND(TIMER_WAIT/1e9,3)                                                        AS ms,
  ROWS_EXAMINED AS examined, ROWS_SENT AS sent, NO_INDEX_USED AS no_index, EVENT_ID AS event_id
FROM performance_schema.events_statements_history_long
WHERE SQL_TEXT LIKE '/* reqId=%';
ALTER TABLE trace_snapshot
  MODIFY repo VARCHAR(40), MODIFY method VARCHAR(60),
  ADD INDEX(reqId), ADD INDEX(uri), ADD INDEX(repo);"
MY qaskerdb -t -e "SELECT uri, COUNT(DISTINCT reqId) reqs, ROUND(COUNT(*)/COUNT(DISTINCT reqId),1) q_per_req, ROUND(SUM(examined)/COUNT(DISTINCT reqId)) ex_per_req FROM trace_snapshot GROUP BY uri ORDER BY q_per_req DESC;"
echo "[trace] done"
