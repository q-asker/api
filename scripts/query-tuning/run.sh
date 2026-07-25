#!/usr/bin/env bash
# 쿼리 튜닝 스케일 스윕 오케스트레이터 (loadgen·run-level 인라인 통합).
#  3레벨(x1/x10/x100)을 순차로: 레벨별 DB에 앱을 붙여 부하(loadgen)를 태우고 요청을 trace_snapshot 에 귀속.
#  레벨→포트→컨테이너 매핑을 여기 박아 손 루프(단어분리 실수)를 없앤다. 부하 파라미터(ROUNDS 등)는 env 로 넘긴다.
#  예: ROUNDS=50 bash run.sh   /   bash run.sh x100   (특정 레벨만 = 옛 run-level 직접 실행 대체)
#
#  구조: loadgen()·run_level() 은 본문이 () 서브셸인 함수 — 각자의 set/trap EXIT/exit/지역 함수 재정의가
#        부모(run.sh)로 새지 않아 옛 "별도 프로세스 실행"과 동일하게 격리된다.
#          · loadgen()  = 실 엔드포인트 부하 레시피(구 loadgen.sh). 항상 `loadgen | tail` 로 호출.
#          · run_level() = 레벨 1개 실행(구 run-level.sh): exporter 재지정→앱 기동→loadgen 2패스→trace_snapshot.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE/../.."   # api 루트 — run_level/loadgen 의 상대경로(gradlew·app/…)·서브셸이 이 cwd 를 상속

# ═══════════════════════════════════════════════════════════════════════════════════════
# loadgen() │ 실 엔드포인트 부하 레시피 (구 loadgen.sh) — 읽기 GET + 실 write + 스케줄러 + refresh + SSE + 로그아웃
# ═══════════════════════════════════════════════════════════════════════════════════════
#  mock 서비스가 write를 save→delete로 자기정리(순증 0)하므로 실 write 엔드포인트를 그대로 때린다.
#  커버리지: admin(권한)·/local(하네스 인프라)·/upload-doc(multipart+외부IO, DB쿼리 없음)·/auth/test(레포 없음)를
#  제외한 모든 엔드포인트가 요청된다. SSE 생성구독·로그아웃은 세션/토큰 미매칭이라 net 0로 조회 경로만 태운다.
#  전제: 앱이 local,loadtest,mock 로 떠 있어야 함. 입력 env: BASE·USER_ID(run_level 이 주입)·ROUNDS 등.
loadgen() (
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
USER_ID="${USER_ID:-}"   # 유효 user_id 필수(run_level 이 대상 DB 에서 자동 주입; standalone 이면 직접 지정)
ROUNDS="${ROUNDS:-10}"                  # 읽기+쓰기 엔드포인트 반복(각 엔드포인트 호출 횟수)
DETAIL_SAMPLE="${DETAIL_SAMPLE:-25}"    # templated GET당 대입 id 표본
SCHED_ROUNDS="${SCHED_ROUNDS:-10}"      # 스케줄러 트리거 반복
REFRESH_CONC="${REFRESH_CONC:-20}"      # refresh 동시 요청 수
REFRESH_ROUNDS="${REFRESH_ROUNDS:-50}"  # refresh 버스트 라운드
command -v jq >/dev/null || { echo "jq 필요" >&2; exit 1; }
[ -n "$USER_ID" ] || { echo "[mint] USER_ID 미설정 — run_level 이 자동 주입하거나, DB 에 존재하는 user_id 를 USER_ID 로 넘기세요" >&2; exit 1; }

# ── 1) 토큰 ──
TOKEN=$(curl -s "$BASE/local/token?userId=$USER_ID")
case "$TOKEN" in unknown*|"") echo "[mint] 토큰 발급 실패: $TOKEN" >&2; exit 1 ;; esac
AUTH=(-H "Authorization: Bearer $TOKEN"); JSON=(-H "Content-Type: application/json")
echo "[mint] token ok (${#TOKEN} chars)"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/auth/refresh" -b "refresh_token=preflight")
[[ "$code" == "429" ]] && echo "[warn] /auth/refresh=429 — 레이트리밋 켜짐. local 프로파일 확인." >&2

# ── 2) 읽기 GET 동적 열거(관리·계측·생성·스트림 제외) ──
APIDOC=$(curl -s "$BASE/v3/api-docs")
GET_PATHS=()
while IFS= read -r l; do [ -n "$l" ] && GET_PATHS+=("$l"); done < <(
  echo "$APIDOC" | jq -r '.paths | to_entries[] | select(.value.get != null) | .key' \
  | grep -vE '/(admin|actuator|local)(/|$)|api-docs|swagger|generation|stream')
STATIC=(); TEMPLATED=()
for p in "${GET_PATHS[@]}"; do
  [[ "$p" == /boards || "$p" == /boards/* ]] && continue  # boards: 전용 처리(GET /boards 는 category 필수, boardId 는 숫자 Long)
  [[ "$p" == *"{"* ]] && TEMPLATED+=("$p") || STATIC+=("$p")
done
BOARD_CATS=(INQUIRY UPDATE_LOG)  # GET /boards?category= 필수값(미지정 시 400 → findByCategory 미실행)
echo "[enum] static GET=${#STATIC[@]}  templated GET=${#TEMPLATED[@]}  board cats=${#BOARD_CATS[@]}"

# ── 실 write 엔드포인트(mock 자기정리, 순증 0). __ID__→수확 id, __BID__→숫자 boardId, __SID__→uuid ──
WRITES=(
  "POST|/boards|{\"title\":\"mock\",\"content\":\"mock\"}"
  "PUT|/boards/__BID__|{\"title\":\"mock\",\"content\":\"mock\"}"
  "DELETE|/boards/__BID__|"
  "POST|/feedback|{\"content\":\"mock\"}"
  "POST|/essay/problem-sets/__ID__/problems/1/grade|{\"textAnswer\":\"mock\",\"attemptCount\":1}"
  "PATCH|/user/nickname|{\"nickname\":\"mock\"}"
  "PATCH|/problem-set/__ID__/title|{\"title\":\"mock\"}"
  "POST|/history/init|{\"problemSetId\":\"__ID__\",\"title\":\"mock\"}"
  "POST|/history|{\"problemSetId\":\"__ID__\",\"title\":\"mock\",\"userAnswers\":[],\"score\":0,\"totalTime\":\"0\"}"
  "PATCH|/history/__ID__/title|{\"title\":\"mock\"}"
  "DELETE|/history/__ID__|"
  "DELETE|/history/all|"
  "POST|/folders|{\"name\":\"mock\"}"
  "PATCH|/folders/__ID__|{\"name\":\"mock\"}"
  "PATCH|/history/__ID__/folder|{\"folderId\":null}"
  "DELETE|/folders/__ID__|"
  "POST|/generation|{\"sessionId\":\"__SID__\",\"uploadedUrl\":\"mock\",\"title\":\"mock\",\"quizCount\":5,\"quizType\":\"MULTIPLE\",\"pageNumbers\":[1],\"language\":\"KO\"}"
)

IDPOOL=$(mktemp); BIDPOOL=$(mktemp)   # IDPOOL: hashid(대부분 엔드포인트) · BIDPOOL: 숫자 boardId(board 전용)
trap 'rm -f "$IDPOOL" "$BIDPOOL"' EXIT

harvest() {  # static GET 응답에서 hashid 수확 + board 목록에서 숫자 boardId 수확
  for p in "${STATIC[@]}"; do
    curl -s --max-time 10 "${AUTH[@]}" "$BASE$p" | jq -r '[.. | strings] | .[]' 2>/dev/null \
      | grep -aE '^[A-Za-z0-9_-]{8,}$' >> "$IDPOOL" || true
  done
  sort -u "$IDPOOL" -o "$IDPOOL"
  for c in "${BOARD_CATS[@]}"; do  # /boards 는 category 필수 → 목록 응답의 posts[].boardId(숫자) 수확
    curl -s --max-time 10 "${AUTH[@]}" "$BASE/boards?category=$c&page=0&size=100" \
      | jq -r '.posts[]?.boardId // empty' 2>/dev/null >> "$BIDPOOL" || true
  done
  sort -un "$BIDPOOL" -o "$BIDPOOL"
}
hit_reads() {  # static + templated GET 1회씩 + board 목록(findByCategory)·상세(findByIdWithReplies) 전용
  for p in "${STATIC[@]}"; do curl -s -o /dev/null --max-time 10 "${AUTH[@]}" "$BASE$p" || true; done
  for c in "${BOARD_CATS[@]}"; do
    curl -s -o /dev/null --max-time 10 "${AUTH[@]}" "$BASE/boards?category=$c&page=0&size=100" || true
  done
  if [[ -s "$BIDPOOL" ]]; then
    local BIDS=(); while IFS= read -r l; do [ -n "$l" ] && BIDS+=("$l"); done < <(head -n "$DETAIL_SAMPLE" "$BIDPOOL")
    for bid in "${BIDS[@]}"; do curl -s -o /dev/null --max-time 10 "${AUTH[@]}" "$BASE/boards/$bid" || true; done
  fi
  [[ -s "$IDPOOL" ]] || return 0
  local IDS=(); while IFS= read -r l; do [ -n "$l" ] && IDS+=("$l"); done < <(head -n "$DETAIL_SAMPLE" "$IDPOOL")
  for p in "${TEMPLATED[@]}"; do for id in "${IDS[@]}"; do
    curl -s -o /dev/null --max-time 10 "${AUTH[@]}" "$BASE$(echo "$p" | sed -E 's/\{[^}]+\}/'"$id"'/g')" || true
  done; done
}
hit_writes() {  # $1=대입 hashid. 실 write 엔드포인트 1회씩(mock 순증 0)
  local id="$1" bid e m p b args
  bid=$(head -n1 "$BIDPOOL"); [ -z "$bid" ] && bid=1  # board write 는 숫자 id(mock 이 값 무시, Long 파싱만 통과)
  for e in "${WRITES[@]}"; do
    IFS='|' read -r m p b <<< "$e"; p="${p//__ID__/$id}"; p="${p//__BID__/$bid}"
    args=(-s -o /dev/null --max-time 15 "${AUTH[@]}" -X "$m")
    [ -n "$b" ] && { b="${b//__SID__/$(uuidgen | tr 'A-Z' 'a-z')}"; args+=("${JSON[@]}" -d "$b"); }
    curl "${args[@]}" "$BASE$p" || true
  done
}
sched_burst() {  # 비-controller 스케줄러 SELECT 를 온디맨드로
  for ((i=0; i<SCHED_ROUNDS; i++)); do
    curl -s -o /dev/null --max-time 60 "${AUTH[@]}" -X POST "$BASE/local/scheduler/stale-generation" || true
  done
}
refresh_burst() {  # 가짜 쿠키 동시 요청 — rt_hash 무인덱스 FOR UPDATE 풀스캔 후 miss
  for ((r=0; r<REFRESH_ROUNDS; r++)); do
    for ((c=0; c<REFRESH_CONC; c++)); do
      curl -s -o /dev/null --max-time 10 -X POST "$BASE/auth/refresh" -b "refresh_token=bogus-$RANDOM-$r-$c" &
    done
    wait
  done
}
gen_stream_burst() {  # SSE 생성 구독 — 유효 UUID 로 findGenerationStatusBySessionId 태우고 --max-time 로 끊음
  #  마스킹으로 실 session_id 는 h_(비-UUID)라 @UUID 검증에 막힘 + mock 생성은 롤백이라 세션 미존재.
  #  랜덤 UUID 는 검증만 통과 → 세션 조회 1회 실행(구독 전 동기) 후 emitter 는 --max-time 으로 버림.
  for ((r=0; r<SCHED_ROUNDS; r++)); do
    curl -s -o /dev/null --max-time 2 "${AUTH[@]}" "$BASE/generation/$(uuidgen | tr 'A-Z' 'a-z')/stream" &
  done
  wait
}
logout_burst() {  # 로그아웃 — bogus refresh 쿠키로 폐기(revoke) 경로 태움(매칭 없음 → net 0)
  for ((r=0; r<SCHED_ROUNDS; r++)); do
    curl -s -o /dev/null --max-time 5 "${AUTH[@]}" -X POST "$BASE/auth/logout" -b "refresh_token=bogus-$RANDOM-$r" || true
  done
}

if [[ -n "${DRY_RUN:-}" ]]; then
  echo "── 읽기 GET: static ${STATIC[*]} / templated ${TEMPLATED[*]}"
  echo "── board 전용: 목록 /boards?category={${BOARD_CATS[*]}} + 상세 /boards/{숫자 boardId}"
  echo "── 실 write: ${#WRITES[@]}개 x ROUNDS=$ROUNDS"
  echo "── 스케줄러 x$SCHED_ROUNDS / refresh $REFRESH_CONC x $REFRESH_ROUNDS = $((REFRESH_CONC*REFRESH_ROUNDS))발"
  echo "── SSE 생성구독 x$SCHED_ROUNDS / 로그아웃 x$SCHED_ROUNDS (요청가능 커버리지)"
  echo "[dry-run] 종료."; exit 0
fi

harvest
ID=$(head -n1 "$IDPOOL"); [ -z "$ID" ] && ID=mock
echo "[load] 읽기+쓰기 엔드포인트 x$ROUNDS 라운드 (대입 id=$ID)"
for ((i=0; i<ROUNDS; i++)); do hit_reads; hit_writes "$ID"; done
echo "[load] 스케줄러 트리거 x$SCHED_ROUNDS"; sched_burst
echo "[load] refresh 경합 $REFRESH_CONC x $REFRESH_ROUNDS (총 $((REFRESH_CONC*REFRESH_ROUNDS))발)"; refresh_burst
echo "[load] SSE 생성구독 x$SCHED_ROUNDS"; gen_stream_burst
echo "[load] 로그아웃 x$SCHED_ROUNDS"; logout_burst
echo "[done]"
)

# ═══════════════════════════════════════════════════════════════════════════════════════
# run_level() │ 스케일 레벨 1개 실행 (구 run-level.sh) — 앱을 해당 DB에 붙여 수집→부하→trace_snapshot→종료
# ═══════════════════════════════════════════════════════════════════════════════════════
#  인자: run_level <PORT> <CONTAINER> <LABEL>. () 서브셸이라 trap EXIT(앱 kill)·exit 가 이 레벨에만 스코프된다.
run_level() (
set -euo pipefail
PORT="$1"; CONTAINER="$2"; LABEL="$3"
# 최신 소스로 bootJar 재빌드 — build/libs 에 남은 이전(예: enhancement 하네스) jar 를 주워
#  오염된 측정을 하지 않도록 한다. quiz-set-impl clean 은 이전 인핸스먼트 모드 클래스 잔재 제거용.
#  run.sh 스윕은 앞단에서 한 번만 빌드하고 LT_SKIP_BUILD=1 로 레벨별 중복 빌드를 건너뛴다.
if [ -z "${LT_SKIP_BUILD:-}" ]; then
  echo "[$LABEL] bootJar 재빌드"
  ./gradlew :quiz-set-impl:clean :app:bootJar -q
fi
# -plain.jar(Main-Class 없는 비실행 jar)는 제외 — 실행 가능한 bootJar 산출물만 고른다.
JAR="$(ls -t app/build/libs/app-*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -1)"
LOG="/tmp/qasker-lt-$LABEL.log"

echo "════════ 레벨 $LABEL ($CONTAINER:$PORT) ════════"
# 레벨 DB 기동 보장 — 정지 상태면 start 후 준비 대기(손으로 껐다 켤 필요 없음).
#  컨테이너 자체가 없으면 재시딩(수백만 행)이 필요하므로 자동 생성하지 않고 안내 후 중단.
if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
  echo "[$LABEL] 컨테이너 $CONTAINER 없음 — provision-level.sh + seed-scale.sh 로 먼저 생성·시딩하세요(README 1·2)"; exit 1
fi
if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" != "true" ]; then
  docker start "$CONTAINER" >/dev/null
  echo -n "[$LABEL] $CONTAINER 기동 대기"
  until docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -N -e "SELECT 1" 2>/dev/null | grep -q 1; do
    echo -n "."; sleep 1
  done
  echo " ready"
fi
# 이전 앱 잔재 정리
lsof -ti:8080 2>/dev/null | xargs kill -9 2>/dev/null || true

# 0) mysqld-exporter를 이 레벨 DB로 재지정 (풀스캔율 패널이 이 레벨을 반영하도록)
#    MySQL 컨테이너가 127.0.0.1 바인딩이라 host.docker.internal:$PORT 로는 못 붙는다(loopback 포트는
#    컨테이너에서 안 보임). 같은 모니터링 네트워크에서 <컨테이너명>:3306 으로 내부 접근한다
#    (컨테이너는 provision-level.sh 로 local_local-monitoring 네트워크에 생성돼 있어야 함).
docker rm -f local-mysqld-exporter >/dev/null 2>&1 || true
docker run -d --name local-mysqld-exporter --network local_local-monitoring \
  -e MYSQLD_EXPORTER_PASSWORD=password \
  prom/mysqld-exporter:v0.16.0 --mysqld.address=$CONTAINER:3306 \
  --mysqld.username=root --collect.info_schema.innodb_metrics --collect.perf_schema.tableiowaits >/dev/null
echo "[$LABEL] exporter → $CONTAINER:3306"

# 1) 앱 기동 — datasource를 이 레벨 포트로 override (env가 loadtest.yml보다 우선)
export JASYPT_ENCRYPTOR_PASSWORD="$(grep '^JASYPT_ENCRYPTOR_PASSWORD=' app/gradle.properties | cut -d= -f2-)"
export SPRING_PROFILES_ACTIVE=local,loadtest,mock  # mock: loadgen이 실 write 엔드포인트를 순증 0으로 태움
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$PORT/qaskerdb"
# 이 레벨의 모든 app 메트릭에 seed 라벨 부여 → 레포 타이밍을 seed축으로 비교 (app-first)
export MANAGEMENT_METRICS_TAGS_SEED="$LABEL"
: > "$LOG"
java -jar "$JAR" >> "$LOG" 2>&1 &
APP_PID=$!
# SIGKILL 로 즉시 종료 — SIGTERM(graceful)이면 loadgen이 열어둔 SSE 요청이 끝나길 기다려
#  app-common.yml 의 timeout-per-shutdown-phase(300s)를 매 레벨 다 채우고, 그동안 wait 가 블록돼
#  스윕이 다음 레벨로 못 넘어간다. 일회용 부하 JVM + 일회용 분석 DB(mock 쓰기 순증 0)라 강제종료 안전.
trap 'kill -9 $APP_PID 2>/dev/null || true; wait $APP_PID 2>/dev/null || true' EXIT

for i in $(seq 1 45); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://localhost:8080/v3/api-docs || echo 000)
  [ "$code" = "200" ] && { echo "[$LABEL] app up (~$((i*2))s)"; break; }
  kill -0 $APP_PID 2>/dev/null || { echo "[$LABEL] 부팅 실패"; grep -iE "APPLICATION FAILED|Caused by" "$LOG" | head -3; exit 1; }
  sleep 2
done
grep -m1 "jdbc:mysql://127.0.0.1:$PORT" "$LOG" >/dev/null && echo "[$LABEL] datasource=$PORT 확인"

# 1b) slow log 수집 켜기·리셋 (대시보드 '부록 — 슬로우 쿼리 로그' 패널이 mysql.slow_log 를 읽는다).
#     >0.1s + 무인덱스 쿼리를 이번 실행분만 기록. 분석 컨테이너는 일회용이라 원복 불필요.
docker exec -i -e MYSQL_PWD=password "$CONTAINER" mysql -uroot >/dev/null 2>&1 <<'SQL'
SET GLOBAL slow_query_log = 1;
SET GLOBAL log_output = 'TABLE';
SET GLOBAL long_query_time = 0.1;
SET GLOBAL log_queries_not_using_indexes = 1;
TRUNCATE mysql.slow_log;
SQL
echo "[$LABEL] slow log 수집 ON (0.1s) + 리셋"

# 유효 user_id 를 대상 DB 에서 동적 선택(하드코딩 방지 — 마스킹/시딩으로 user_id 가 바뀌어도 안전)
USER_ID=$(docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -N qaskerdb -e "SELECT user_id FROM user LIMIT 1" 2>/dev/null)
[ -n "$USER_ID" ] || { echo "[$LABEL] user 테이블에서 user_id 를 못 가져옴 — 시딩(seed-scale) 확인"; exit 1; }
export USER_ID
echo "[$LABEL] loadgen USER_ID=$USER_ID"

# 2) 무거운 부하 — loadgen(실 엔드포인트) → Micrometer seed 라벨 → §① 스케일 지연곡선
export ROUNDS="${ROUNDS:-50}" DETAIL_SAMPLE="${DETAIL_SAMPLE:-25}" REFRESH_ROUNDS="${REFRESH_ROUNDS:-60}" REFRESH_CONC="${REFRESH_CONC:-20}" SCHED_ROUNDS="${SCHED_ROUNDS:-10}"
T0=$(($(date +%s) * 1000))
BASE=http://localhost:8080 loadgen 2>&1 | tail -3
T1=$(($(date +%s) * 1000))

# 2b) Grafana annotation — 이 레벨 구간을 타임라인에 라벨로 표시
curl -s -u admin:admin -H "Content-Type: application/json" -X POST \
  http://localhost:3000/api/annotations \
  -d "{\"time\":$T0,\"timeEnd\":$T1,\"tags\":[\"loadtest\",\"$LABEL\"],\"text\":\"$LABEL — $CONTAINER:$PORT\"}" >/dev/null \
  && echo "[$LABEL] annotation 등록"

# 3) 요청 귀속 스냅샷 — 가벼운 loadgen 패스 + trace_snapshot.
#    스케일 패스(위 무거운 loadgen)와 볼륨이 달라 별 패스로 뜬다: history_long(10k 링버퍼)에 맞춰 가볍게 태운다.
#    → 이 레벨 DB 의 uri·repo.method별 examined 가 trace_snapshot 에 남아 §②③를 채운다(레벨마다 자기 DB에 저장).
echo "[$LABEL] 요청 귀속 트레이스(가벼운 패스):"
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -e "
  UPDATE performance_schema.setup_consumers SET ENABLED='YES' WHERE NAME='events_statements_history_long';
  TRUNCATE performance_schema.events_statements_history_long;" 2>/dev/null
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb -e "DROP TABLE IF EXISTS trace_snapshot;" 2>/dev/null
ROUNDS=3 DETAIL_SAMPLE=10 SCHED_ROUNDS=5 REFRESH_CONC=10 REFRESH_ROUNDS=8 \
  BASE=http://localhost:8080 loadgen 2>&1 | tail -4
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb -e "
CREATE TABLE trace_snapshot AS
SELECT
  CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'reqId=',-1),' ',1) AS CHAR(16)) AS reqId,
  CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'uri=',-1),' ',1) AS CHAR(80))   AS uri,
  LEFT(TRIM(SUBSTRING_INDEX(SQL_TEXT,'*/',-1)),150)                              AS sql_stripped,
  -- repo·method는 앱이 심은 repoMethod= 주석에서 파싱(repoMethod=BoardRepository.save → repo=BoardRepository, method=save).
  --  레포 메서드를 안 거친 SQL(lazy-load·dirty-check UPDATE 등)은 주석이 비므로 테이블명으로 repo 복원 + method='Hibernate query'.
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'repoMethod=',-1),' ',1),'.', 1),''),
    CASE
      WHEN SQL_TEXT LIKE '%refresh_token%'   THEN 'RefreshTokenRepository'
      WHEN SQL_TEXT LIKE '%quiz_history%'    THEN 'QuizHistoryRepository'
      WHEN SQL_TEXT LIKE '%essay_grade_log%' THEN 'EssayGradeLogRepository'
      WHEN SQL_TEXT LIKE '%feedback_board%'  THEN 'FeedbackBoardRepository'
      -- 테이블명 뒤 경계(공백/괄호)로 앵커링한다. 컬럼명 problem_set_id 가 %problem_set% 에 걸려
      --  problem 테이블 SQL(lazy explanation 로딩 등)이 ProblemSet 으로 오귀속되던 버그 방지.
      WHEN SQL_TEXT LIKE '%problem\_set %' OR SQL_TEXT LIKE '%problem\_set(%' THEN 'ProblemSetRepository'
      WHEN SQL_TEXT LIKE '%problem %'      OR SQL_TEXT LIKE '%problem(%'      THEN 'ProblemRepository'
      WHEN SQL_TEXT LIKE '%board%'           THEN 'BoardRepository'
      WHEN SQL_TEXT LIKE '%reply%'           THEN 'ReplyRepository'
      WHEN SQL_TEXT LIKE '%user%'            THEN 'UserRepository'
      ELSE '(기타)' END)                                                        AS repo,
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'repoMethod=',-1),' ',1),'.',-1),''),
    'Hibernate query')                                                          AS method,
  ROUND(TIMER_WAIT/1e9,3) AS ms,
  ROWS_EXAMINED AS examined, ROWS_SENT AS sent, NO_INDEX_USED AS no_index, EVENT_ID AS event_id
FROM performance_schema.events_statements_history_long
WHERE SQL_TEXT LIKE '/* reqId=%';
ALTER TABLE trace_snapshot
  MODIFY repo VARCHAR(40), MODIFY method VARCHAR(60),
  ADD INDEX(reqId), ADD INDEX(uri), ADD INDEX(repo);" 2>/dev/null
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb -t -e "
SELECT uri, COUNT(DISTINCT reqId) reqs, ROUND(COUNT(*)/COUNT(DISTINCT reqId),1) q_per_req,
       ROUND(SUM(examined)/COUNT(DISTINCT reqId)) ex_per_req
FROM trace_snapshot GROUP BY uri ORDER BY q_per_req DESC;" 2>/dev/null

# 4) 스크레이프 정착 — 앱이 죽기 전에 Prometheus가 최종 카운터를 긁게 대기(>5s scrape_interval).
#    안 그러면 끝에 몰린 짧은 부하(sched·refresh)의 §① series 가 스크레이프 눈금 사이로 밀려 누락된다.
echo "[$LABEL] Prometheus 스크레이프 정착 대기(10s)"
sleep 10

echo "[$LABEL] done"
)

# ═══════════════════════════════════════════════════════════════════════════════════════
# 메인 │ 3레벨 순차 스윕
# ═══════════════════════════════════════════════════════════════════════════════════════
# 레벨 정의: "LABEL PORT CONTAINER"
LEVELS=(
  "x1   3307 local-mysql-x1"
  "x10  3308 local-mysql-x10"
  "x100 3309 local-mysql-x100"
)

want="${1:-all}"   # all | x1 | x10 | x100

# 스윕 전 bootJar 1회 재빌드(최신 소스 반영) → 레벨별 run_level 은 LT_SKIP_BUILD 로 재빌드 생략.
#  build/libs 에 남은 이전(예: enhancement 하네스) jar 오염을 원천 차단한다.
echo "[run.sh] bootJar 재빌드(1회)"
./gradlew :quiz-set-impl:clean :app:bootJar -q
export LT_SKIP_BUILD=1

for spec in "${LEVELS[@]}"; do
  read -r label port container <<< "$spec"
  [ "$want" != "all" ] && [ "$want" != "$label" ] && continue
  echo "════════════════ run.sh: $label ════════════════"
  # 다른 레벨 컨테이너 정지 — 4G 버퍼풀 MySQL 이 동시 상주하면 RAM 압박으로 네이티브 JVM 이
  #  OS 에 강제종료(SIGKILL)되고, 이어지는 가벼운 패스가 죽은 앱에 붙지 못해 스윕이 중단된다.
  for other in "${LEVELS[@]}"; do
    read -r _ol _op oc <<< "$other"
    [ "$oc" != "$container" ] && docker stop "$oc" >/dev/null 2>&1 || true
  done
  run_level "$port" "$container" "$label"
  # 중간 레벨(x1·x10)은 정지해 RAM 반환(한 번에 한 레벨). x100은 대시보드가 3309를 읽으므로 유지.
  if [ "$label" != "x100" ]; then
    docker stop "$container" >/dev/null 2>&1 && echo "[run.sh] $container 정지(RAM 반환)"
  fi
done
echo "[run.sh] 완료 (${want})"
