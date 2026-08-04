#!/usr/bin/env bash
# 쿼리 튜닝 스케일 스윕 오케스트레이터 (loadgen·run-level 인라인 통합).
#  3레벨(x1/x10/x100)을 순차로: 레벨별 DB에 앱을 붙여 부하(loadgen)를 태우고 요청을 trace_snapshot 에 귀속.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE/../.."   # api 루트 — run_level/loadgen 의 상대경로(gradlew·app/…)·서브셸이 이 cwd 를 상속

# ═══════════════════════════════════════════════════════════════════════════════════════
# loadgen() │ 실 엔드포인트 부하 레시피 (구 loadgen.sh) — 읽기 GET + 실 write + 스케줄러 + refresh + SSE + 로그아웃
# ═══════════════════════════════════════════════════════════════════════════════════════
#  mock 서비스가 write를 save→delete로 자기정리(순증 0)하므로 실 write 엔드포인트를 그대로 때린다.
#   예외: POST /generation 은 실 저장(mock 픽스처 커밋, E2E 시드 겸용) — seed-vuser.sql 이 매 실행 vu 소유분을 정리.
#  커버리지: admin(권한)·/local(하네스 인프라)·/upload-doc(multipart+외부IO, DB쿼리 없음)·/auth/test(레포 없음)를
#  제외한 모든 엔드포인트가 요청된다. SSE 생성구독·로그아웃은 세션/토큰 미매칭이라 net 0로 조회 경로만 태운다.
#  균일 모델: "가상 유저(vu_loadtest) 1명, 각 엔드포인트를 CONC(기본 10)회 동시 요청"을 ROUNDS 라운드 반복.
#   엔드포인트별 제각각이던 옛 버스트를 폐기 → trace_snapshot reqs 가 균일해 누락(깨진 엔드포인트) 탐지 쉬움.
#   페이로드는 x1 원본 실측 크기(실측 avg 바이트) 고정 문자열. 전제: 앱이 local,mock 로 기동.
#  입력 env: BASE·USER_ID(vu, run_level 주입)·ADMIN_USER_ID·CONC(10)·ROUNDS.
loadgen() (
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
USER_ID="${USER_ID:-}"   # 가상 유저(vu_loadtest) — run_level 이 주입. 이 한 유저 토큰으로 전 user 엔드포인트를 태운다.
ADMIN_USER_ID="${ADMIN_USER_ID:-}"   # ROLE_ADMIN 유저(있으면 admin 토큰 발급 + admin 패스 활성)
ADMIN_SET_IDS="${ADMIN_SET_IDS:-}"   # admin GET quality-review 에 넣을 실 problem_set id(공백 구분)
# 균일 모델: "가상 유저 1명, 각 엔드포인트를 CONC 회 동시 요청". 옛 엔드포인트별 제각각 횟수(refresh 버스트 등)를 폐기.
CONC="${CONC:-10}"       # 각 엔드포인트 동시 요청 수
ROUNDS="${ROUNDS:-10}"   # 전 엔드포인트 1패스 = 1라운드, ROUNDS 회 반복(§① 표본 크기)
command -v jq >/dev/null || { echo "jq 필요" >&2; exit 1; }
[ -n "$USER_ID" ] || { echo "[mint] USER_ID 미설정 — run_level 이 자동 주입하거나, DB 에 존재하는 user_id 를 USER_ID 로 넘기세요" >&2; exit 1; }

# ── 1) 토큰 ──
TOKEN=$(curl -s "$BASE/local/token?userId=$USER_ID")
case "$TOKEN" in unknown*|"") echo "[mint] 토큰 발급 실패: $TOKEN" >&2; exit 1 ;; esac
AUTH=(-H "Authorization: Bearer $TOKEN"); JSON=(-H "Content-Type: application/json")
echo "[mint] user token ok (${#TOKEN} chars)"
# admin 토큰(선택) — 사용자 엔드포인트는 위 user 토큰, admin 엔드포인트만 이 토큰을 쓴다(이중 토큰).
ADMIN_AUTH=()
if [ -n "$ADMIN_USER_ID" ]; then
  ADMIN_TOKEN=$(curl -s "$BASE/local/token?userId=$ADMIN_USER_ID")
  case "$ADMIN_TOKEN" in
    unknown*|"") echo "[mint] admin 토큰 발급 실패($ADMIN_TOKEN) — admin 패스 생략" >&2 ;;
    *) ADMIN_AUTH=(-H "Authorization: Bearer $ADMIN_TOKEN"); echo "[mint] admin token ok (${#ADMIN_TOKEN} chars)" ;;
  esac
fi
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/auth/refresh" -b "refresh_token=preflight")
# mock 프로파일은 레이트리밋 off 가 전제 → 429 면 오설정. 진행하면 전 부하가 튕겨 빈 결과를 진짜 데이터로 오독하므로 중단.
[[ "$code" == "429" ]] && { echo "[abort] /auth/refresh=429 — 레이트리밋 켜짐(mock 프로파일 확인). 측정 무효 → 중단." >&2; exit 1; }

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

# ── 실측 페이로드(x1 원본 행 avg bytes, 마스킹 SAFE 컬럼 실측) — 결정적 고정 ASCII 문자열(랜덤 아님). ──
#  실측 avg 를 바이트 그대로 재현(ASCII 1B/자). mock 이 요청 바디를 throwaway 엔티티에 넣는 경로만 INSERT 크기에 반영된다.
pad(){ head -c "$1" </dev/zero | tr '\0' x; }   # N바이트 'x'
P_BOARD_TITLE=$(pad 77)       # board.title avg 77B
P_BOARD_CONTENT=$(pad 1357)   # board.content avg 1357B(14KB 이상치 포함 평균)
P_FEEDBACK=$(pad 94)          # feedback_board.content avg 94B
P_ESSAY=$(pad 223)            # essay.student_answer avg 223B (≤1000 cap)
P_HIST_TITLE=$(pad 52)        # quiz_history.title avg 52B (≤100 cap)
P_PS_TITLE=$(pad 50)          # problem_set.title avg 50B (≤100 cap)
P_FOLDER=$(pad 12)            # quiz_folder.name avg 12B (표본 약함)
P_NICK=$(pad 20)              # nickname: 실측 34는 HASH산물 → API cap 20 채택
# userAnswers JSON ≈ quiz_history.answers avg 1089B — UserAnswer 2개(각 textAnswer≤1000 cap). (mock 은 아직 answers 미반영 — 요청 크기만 실측)
UA_T=$(pad 510)
UA_JSON="[{\"number\":1,\"userAnswer\":1,\"inReview\":false,\"textAnswer\":\"$UA_T\"},{\"number\":2,\"userAnswer\":2,\"inReview\":false,\"textAnswer\":\"$UA_T\"}]"

# ── 실 write 엔드포인트(generation 만 실 저장·seed-vuser 정리, 나머지 mock 자기정리 순증 0). __ID__→수확 id, __BID__→숫자 boardId, __SID__→uuid ──
WRITES=(
  "POST|/boards|{\"title\":\"$P_BOARD_TITLE\",\"content\":\"$P_BOARD_CONTENT\"}"
  "PUT|/boards/__BID__|{\"title\":\"$P_BOARD_TITLE\",\"content\":\"$P_BOARD_CONTENT\"}"
  "DELETE|/boards/__BID__|"
  "POST|/feedback|{\"content\":\"$P_FEEDBACK\"}"
  "POST|/essay/problem-sets/__ID__/problems/1/grade|{\"textAnswer\":\"$P_ESSAY\",\"attemptCount\":1}"
  "PATCH|/user/nickname|{\"nickname\":\"$P_NICK\"}"
  "PATCH|/problem-set/__ID__/title|{\"title\":\"$P_PS_TITLE\"}"
  "POST|/history/init|{\"problemSetId\":\"__ID__\",\"title\":\"$P_HIST_TITLE\"}"
  "POST|/history|{\"problemSetId\":\"__ID__\",\"title\":\"$P_HIST_TITLE\",\"userAnswers\":$UA_JSON,\"score\":0,\"totalTime\":\"00:00:00\"}"
  "PATCH|/history/__ID__/title|{\"title\":\"$P_HIST_TITLE\"}"
  "DELETE|/history/__ID__|"
  "DELETE|/history/all|"
  "POST|/folders|{\"name\":\"$P_FOLDER\"}"
  "PATCH|/folders/__ID__|{\"name\":\"$P_FOLDER\"}"
  "PATCH|/history/__ID__/folder|{\"folderId\":null}"
  "DELETE|/folders/__ID__|"
  "POST|/generation|{\"sessionId\":\"__SID__\",\"uploadedUrl\":\"mock\",\"title\":\"$P_HIST_TITLE\",\"quizCount\":5,\"quizType\":\"MULTIPLE\",\"pageNumbers\":[1],\"language\":\"KO\"}"
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
# 같은 요청을 CONC 회 동시 발사. $1=method $2=path $3=body(opt, __SID__→요청별 uuid). AUTH 토큰 사용.
fire() {
  local m="$1" u="$2" b="${3:-}" i args bb
  for ((i=0; i<CONC; i++)); do
    args=(-s -o /dev/null --max-time 20 "${AUTH[@]}" -X "$m")
    if [ -n "$b" ]; then bb="${b//__SID__/$(uuidgen | tr 'A-Z' 'a-z')}"; args+=("${JSON[@]}" -d "$bb"); fi
    curl "${args[@]}" "$BASE$u" &
  done
  wait
}
# templated GET: 수확 id 표본(최대 CONC개)을 각 1회 동시 대입 — id 다양성 + CONC 동시성을 겸한다.
fire_templated() {
  local tpl="$1" id ids=()
  while IFS= read -r l; do [ -n "$l" ] && ids+=("$l"); done < <(head -n "$CONC" "$IDPOOL")
  [ ${#ids[@]} -gt 0 ] || return 0
  for id in "${ids[@]}"; do
    curl -s -o /dev/null --max-time 20 "${AUTH[@]}" "$BASE$(echo "$tpl" | sed -E "s/\{[^}]+\}/$id/g")" &
  done
  wait
}
# 쿠키 기반(-b)·SSE 는 fire(-d) 로 안 되어 전용 CONC 헬퍼. 각 요청 쿠키/uuid 는 달리해 현실적으로.
refresh_conc() { local i; for ((i=0;i<CONC;i++)); do curl -s -o /dev/null --max-time 10 -X POST "$BASE/auth/refresh" -b "refresh_token=bogus-$RANDOM-$i" & done; wait; }
logout_conc()  { local i; for ((i=0;i<CONC;i++)); do curl -s -o /dev/null --max-time 5  "${AUTH[@]}" -X POST "$BASE/auth/logout" -b "refresh_token=bogus-$RANDOM-$i" & done; wait; }
sse_conc()     { local i; for ((i=0;i<CONC;i++)); do curl -s -o /dev/null --max-time 2  "${AUTH[@]}" "$BASE/generation/$(uuidgen | tr 'A-Z' 'a-z')/stream" & done; wait; }
admin_burst() {  # 방법 B — admin 전용(ROLE_ADMIN 토큰) 전 엔드포인트를 mock net-0 로 태워 admin 레포를 §①(seed)에 편입.
  #  quality-review(GET·POST)=ProblemQualityLog 두 finder 읽기, board admin(POST/PUT/replies)=Board·Reply write.
  #  explanation-review(POST)=실 서비스(mock 없음) — findByProblemSetIdIn 배치 읽기 + 형식 미달분만 review UPDATE.
  #  마킹은 같은 값 재대입이라 2회차부터 dirty 아님(UPDATE 0) → 행 순증 0. hibernate 하네스(mode 축)와는
  #  DB 세션·귀속(reqId)이 분리돼 겹쳐도 서로 오염 없음.
  [ ${#ADMIN_AUTH[@]} -gt 0 ] || return 0
  local bid i; bid=$(head -n1 "$BIDPOOL"); [ -z "$bid" ] && bid=1
  # 각 admin 엔드포인트도 CONC 동시(균일). admin 토큰(ADMIN_AUTH) 사용.
  if [ -n "$ADMIN_SET_IDS" ]; then
    local ids_json; ids_json=$(printf '%s,' $ADMIN_SET_IDS); ids_json="[${ids_json%,}]"
    # GET latestResult(mock) → ProblemQualityLog 읽기. fire_templated 와 동일 규칙: id 표본 각 1회 대입 + CONC 캡
    #  (표본 20개를 그대로 돌리면 이 엔드포인트만 20 동시가 돼 균일 모델이 깨진다. POST 본문 setIds 는 배치 크기라 20 유지.)
    for sid in $(printf '%s\n' $ADMIN_SET_IDS | head -n "$CONC"); do
      curl -s -o /dev/null --max-time 10 "${ADMIN_AUTH[@]}" "$BASE/admin/problem-sets/$sid/quality-review" &
    done; wait
    for ((i=0;i<CONC;i++)); do  # POST quality-review(submitReviewBulk mock) → pass2·explanation finder 읽기
      curl -s -o /dev/null --max-time 30 "${ADMIN_AUTH[@]}" "${JSON[@]}" -X POST "$BASE/admin/problem-sets/quality-review" -d "{\"setIds\":$ids_json}" &
    done; wait
    for ((i=0;i<CONC;i++)); do  # POST explanation-review(실 서비스) → findByProblemSetIdIn 배치 읽기 + 미달분 마킹
      curl -s -o /dev/null --max-time 60 "${ADMIN_AUTH[@]}" "${JSON[@]}" -X POST "$BASE/admin/problem-sets/explanation-review" -d "{\"setIds\":$ids_json}" &
    done; wait
  fi
  # admin 게시판(mock net-0) — Board·Reply admin write 경로, 각 CONC 동시
  for ((i=0;i<CONC;i++)); do curl -s -o /dev/null --max-time 10 "${ADMIN_AUTH[@]}" "${JSON[@]}" -X POST "$BASE/admin/boards/update-logs" -d '{"title":"mock","content":"mock"}' & done; wait
  for ((i=0;i<CONC;i++)); do curl -s -o /dev/null --max-time 10 "${ADMIN_AUTH[@]}" "${JSON[@]}" -X PUT "$BASE/admin/boards/update-logs/$bid" -d '{"title":"mock","content":"mock"}' & done; wait
  for ((i=0;i<CONC;i++)); do curl -s -o /dev/null --max-time 10 "${ADMIN_AUTH[@]}" "${JSON[@]}" -X POST "$BASE/admin/boards/$bid/replies" -d '{"content":"mock"}' & done; wait
}

if [[ -n "${DRY_RUN:-}" ]]; then
  echo "── 균일 모델: 각 엔드포인트 CONC=$CONC 동시 × ROUNDS=$ROUNDS 라운드 (가상 유저 1명)"
  echo "── 읽기 GET: static ${STATIC[*]} / templated ${TEMPLATED[*]}"
  echo "── board: 목록 /boards?category={${BOARD_CATS[*]}} + 상세 /boards/{숫자 boardId}"
  echo "── 실 write: ${#WRITES[@]}개 / 스케줄러·refresh·SSE·로그아웃 / admin 패스"
  echo "[dry-run] 종료."; exit 0
fi

harvest
BID=$(head -n1 "$BIDPOOL"); [ -z "$BID" ] && BID=1       # board write __BID__ 대입(숫자)
IDH=$(head -n1 "$IDPOOL"); [ -z "$IDH" ] && IDH=mock     # write __ID__ 대입(수확 hashid)
echo "[load] 균일 부하 — 각 엔드포인트 CONC=$CONC 동시 × ROUNDS=$ROUNDS 라운드 (vu self-harvest id=$IDH)"
for ((r=0; r<ROUNDS; r++)); do
  # ── 읽기 (각 엔드포인트 CONC 동시) ──
  for p in "${STATIC[@]}"; do fire GET "$p"; done
  for c in "${BOARD_CATS[@]}"; do fire GET "/boards?category=$c&page=0&size=100"; done
  for p in "${TEMPLATED[@]}"; do fire_templated "$p"; done
  fire GET "/boards/$BID"
  # ── 쓰기 (mock 순증 0, 각 CONC 동시) ──
  for e in "${WRITES[@]}"; do
    IFS='|' read -r m p b <<< "$e"; p="${p//__ID__/$IDH}"; p="${p//__BID__/$BID}"
    fire "$m" "$p" "$b"
  done
  # ── 스케줄러·refresh·SSE·로그아웃·admin (각 CONC 동시) ──
  fire POST "/local/scheduler/stale-generation"
  refresh_conc
  sse_conc
  logout_conc
  admin_burst
done
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

# 1) 앱 기동 — datasource를 이 레벨 포트로 override (env가 mock.yml보다 우선)
export JASYPT_ENCRYPTOR_PASSWORD="$(grep '^JASYPT_ENCRYPTOR_PASSWORD=' app/gradle.properties | cut -d= -f2-)"
export SPRING_PROFILES_ACTIVE=local,mock  # mock: loadgen write 자기정리(generation 만 실 저장) + 레이트리밋 off·분석 DB(구 loadtest 흡수)
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$PORT/qaskerdb"
# 이 레벨의 모든 app 메트릭에 seed 라벨 부여 → 레포 타이밍을 seed축으로 비교 (app-first)
export MANAGEMENT_METRICS_TAGS_SEED="$LABEL"
# 튜닝 전후 구분 라벨(PHASE=before|after — 스윕 진입 가드에서 강제). 다른 시계열로 갈려 Prometheus 에 공존 →
#  before/after 를 같은 §① 패널에 나란히 표시(백업 불필요, DB측 trace_snapshot 은 DROP 되므로 별도 백업).
export MANAGEMENT_METRICS_TAGS_PHASE="$PHASE"
# 가상 스레드 비활성 — RequestResourceMetricsFilter 의 ThreadMXBean 힙 할당 계측이 가상 스레드에서
#  -1(JDK-8303251)을 반환해 request.thread.allocated.bytes 가 안 찍힌다. 플랫폼 스레드여야 엔드포인트별
#  힙 델타가 기록된다(hibernate 하네스도 같은 이유로 끈다). SQL 비용(examined·쿼리수) 측정엔 영향 없음.
export SPRING_THREADS_VIRTUAL_ENABLED=false
: > "$LOG"
java -jar "$JAR" >> "$LOG" 2>&1 &
APP_PID=$!
# SIGKILL 로 즉시 종료 — SIGTERM(graceful)이면 loadgen이 열어둔 SSE 요청이 끝나길 기다려
#  app-common.yml 의 timeout-per-shutdown-phase(300s)를 매 레벨 다 채우고, 그동안 wait 가 블록돼
#  스윕이 다음 레벨로 못 넘어간다. 일회용 부하 JVM + 일회용 분석 DB(생성 잔재는 seed-vuser 가 다음 실행에 정리)라 강제종료 안전.
trap 'kill -9 $APP_PID 2>/dev/null || true; wait $APP_PID 2>/dev/null || true' EXIT

for i in $(seq 1 45); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://localhost:8080/v3/api-docs || echo 000)
  [ "$code" = "200" ] && { echo "[$LABEL] app up (~$((i*2))s)"; break; }
  kill -0 $APP_PID 2>/dev/null || { echo "[$LABEL] 부팅 실패"; grep -iE "APPLICATION FAILED|Caused by" "$LOG" | head -3; exit 1; }
  sleep 2
done
grep -m1 "jdbc:mysql://127.0.0.1:$PORT" "$LOG" >/dev/null && echo "[$LABEL] datasource=$PORT 확인"

# 가상 유저(vu_loadtest) 합성 — 기존 실 행을 vu 소유로 복사해 실 크기 데이터로 self-harvest 가능하게(결정적·격리).
#  user 엔드포인트는 이 vu 단일 토큰으로, admin 엔드포인트만 별도 ROLE_ADMIN 토큰(이중 토큰).
echo "[$LABEL] 가상 유저 합성(seed-vuser)"
{ echo "SET @k=30;"; cat "$HERE/seed-vuser.sql"; } \
  | docker exec -i -e MYSQL_PWD=password "$CONTAINER" mysql --default-character-set=utf8mb4 -uroot qaskerdb 2>/dev/null \
  || { echo "[$LABEL] 가상 유저 합성 실패 — 시딩(seed-scale) 확인"; exit 1; }
# 스테일 세트 시딩 — 전체 COMPLETED 리셋 후 최저 id 24건을 FAILED·2시간 전으로 고정(스케줄러 삭제 대상 = deleteAll 발화).
#  problem_set 존재만 전제(GENERATING 불필요)·매 레벨 부하 직전 재적용해 레벨마다 동일 분포. seed-stale-generation.sql 주석 참고.
echo "[$LABEL] 스테일 세트 시딩(seed-stale-generation)"
docker exec -i -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb < "$HERE/seed-stale-generation.sql" 2>/dev/null \
  || echo "[$LABEL] 스테일 시딩 경고(problem_set 없음?) — 무시하고 진행"
USER_ID=vu_loadtest
# admin 토큰용 ROLE_ADMIN 유저 + admin 패스(quality-review)에 넣을 실 problem_set id 표본.
#  품질 로그가 있는 세트만 골라야 ProblemQualityLog finder 가 빈 결과가 아니게 태워진다(없으면 admin 패스 스킵).
ADMIN_USER_ID=$(docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -N qaskerdb -e "SELECT user_id FROM user WHERE role='ROLE_ADMIN' LIMIT 1" 2>/dev/null)
ADMIN_SET_IDS=$(docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -N qaskerdb -e "SELECT DISTINCT problem_set_id FROM problem_quality_log ORDER BY problem_set_id LIMIT 20" 2>/dev/null | tr '\n' ' ')
export USER_ID ADMIN_USER_ID ADMIN_SET_IDS
echo "[$LABEL] loadgen USER_ID=$USER_ID (user 토큰) / ADMIN_USER_ID=${ADMIN_USER_ID:-없음} (admin 토큰)"

# 2) 부하 전 문장 캡처 ON — 부하 전체가 history_long(10만행, provision-level OBS_CONF)에 담기므로
#    §①(Micrometer 스케일 지연곡선)과 §②③(trace_snapshot 귀속)을 같은 단일 패스에서 얻는다
#    (옛 무거운/가벼운 2패스 통합 — 전 엔드포인트 표본이 CONC×ROUNDS 단일 부하로 균일해진다).
#    트랜잭션 제어문(SET autocommit·COMMIT·SET SESSION TRANSACTION·ROLLBACK)은 요청당 ~5문장으로 링의
#    80%를 차지하지만 귀속(reqId 주석 문장)에 안 쓰이는 순수 잡음 — 부하 동안 계측을 꺼서 링을 아낀다(끝나면 원복).
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -e "
  UPDATE performance_schema.setup_consumers SET ENABLED='YES' WHERE NAME='events_statements_history_long';
  UPDATE performance_schema.setup_instruments SET ENABLED='YES' WHERE NAME LIKE 'memory/%';
  UPDATE performance_schema.setup_instruments SET ENABLED='NO', TIMED='NO'
    WHERE NAME IN ('statement/sql/set_option','statement/sql/commit','statement/sql/begin','statement/sql/rollback');
  TRUNCATE performance_schema.events_statements_history_long;" 2>/dev/null
# slow log 수집 ON·리셋 — 부하 직전에 켜서 기록 구간을 부하로 한정한다(대시보드 '부록 — 슬로우 쿼리 로그'가
#  mysql.slow_log 를 읽는데, 시딩 전에 켜면 seed-vuser 의 대량 복사 INSERT 가 uri 주석 없는 행으로 끼어든다).
docker exec -i -e MYSQL_PWD=password "$CONTAINER" mysql -uroot >/dev/null 2>&1 <<'SQL'
SET GLOBAL slow_query_log = 1;
SET GLOBAL log_output = 'TABLE';
SET GLOBAL long_query_time = 0.1;
SET GLOBAL log_queries_not_using_indexes = 1;
TRUNCATE mysql.slow_log;
SQL
echo "[$LABEL] slow log 수집 ON (0.1s) + 리셋"
# 부하 — loadgen(각 엔드포인트 CONC 동시 × ROUNDS 균일) → Micrometer seed 라벨
export CONC="${CONC:-10}" ROUNDS="${ROUNDS:-50}"
T0=$(($(date +%s) * 1000))
BASE=http://localhost:8080 loadgen 2>&1 | tail -3
T1=$(($(date +%s) * 1000))

# 2b) Grafana annotation — 이 레벨 구간을 타임라인에 라벨로 표시
curl -s -u admin:admin -H "Content-Type: application/json" -X POST \
  http://localhost:3000/api/annotations \
  -d "{\"time\":$T0,\"timeEnd\":$T1,\"tags\":[\"loadtest\",\"$LABEL\"],\"text\":\"$LABEL — $CONTAINER:$PORT\"}" >/dev/null \
  && echo "[$LABEL] annotation 등록"

# slow log OFF — 기록 구간을 부하로 한정(아래 trace_snapshot 생성 등 하네스 SQL 이 섞이지 않게). 캡처분은 테이블에 보존.
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -e "
  SET GLOBAL slow_query_log=0; SET GLOBAL log_queries_not_using_indexes=0;" 2>/dev/null

# 3) 요청 귀속 스냅샷 — 위 단일 패스의 SQL 문장 전체를 trace_snapshot 으로 영구화.
#    → 이 레벨 DB 의 uri·repo.method별 examined 가 trace_snapshot 에 남아 §②③를 채운다(레벨마다 자기 DB에 저장).
echo "[$LABEL] 요청 귀속 트레이스:"
# 링버퍼 포화 가드 — 행수가 링 크기에 닿았으면 초기 라운드 문장이 밀려 유실된 것(per-req 평균은 남지만
#  표본 결손). §① Micrometer 축은 무관하므로 중단 대신 경고. 반복되면 provision-level OBS_CONF 를 키운다.
HL_ROWS=$(docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -N -e "SELECT COUNT(*) FROM performance_schema.events_statements_history_long" 2>/dev/null)
HL_SIZE=$(docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -N -e "SELECT @@performance_schema_events_statements_history_long_size" 2>/dev/null)
[ "${HL_ROWS:-0}" -ge "${HL_SIZE:-10000}" ] && echo "⚠️  [$LABEL] history_long 포화(${HL_ROWS}/${HL_SIZE}) — 초기 라운드 문장 유실 가능"
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb -e "
DROP TABLE IF EXISTS trace_snapshot;  -- 멱등: 매 런 fresh 재생성(레벨마다 자기 DB, 누적 아님 → 무조건 DROP)
CREATE TABLE trace_snapshot AS
SELECT
  CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'reqId=',-1),' ',1) AS CHAR(16)) AS reqId,
  CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'uri=',-1),' ',1) AS CHAR(80))   AS uri,
  TRIM(SUBSTRING_INDEX(SQL_TEXT,'*/',-1))                                        AS sql_stripped,
  -- repo·method는 앱이 심은 repoMethod= 주석에서 파싱(repoMethod=BoardRepository.save → repo=BoardRepository, method=save).
  --  레포 메서드를 안 거친 SQL(lazy-load·dirty-check UPDATE 등)은 주석이 비므로 테이블명으로 repo 복원 + method='Hibernate query'.
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'repoMethod=',-1),' ',1),'.', 1),''),
    CASE
      WHEN SQL_TEXT LIKE '%refresh_token%'   THEN 'RefreshTokenRepository'
      WHEN SQL_TEXT LIKE '%quiz_history%'    THEN 'QuizHistoryRepository'
      WHEN SQL_TEXT LIKE '%essay_grade_log%' THEN 'EssayGradeLogRepository'
      WHEN SQL_TEXT LIKE '%feedback_board%'  THEN 'FeedbackBoardRepository'
      WHEN SQL_TEXT LIKE '%problem\_quality\_log%' THEN 'ProblemQualityLogRepository'
      -- 테이블명 뒤 경계(공백/괄호)로 앵커링한다. 컬럼명 problem_set_id 가 %problem_set% 에 걸려
      --  problem 테이블 SQL(lazy explanation 로딩 등)이 ProblemSet 으로 오귀속되던 버그 방지.
      WHEN SQL_TEXT LIKE '%problem\_set %' OR SQL_TEXT LIKE '%problem\_set(%' THEN 'ProblemSetRepository'
      WHEN SQL_TEXT LIKE '%problem %'      OR SQL_TEXT LIKE '%problem(%'      THEN 'ProblemRepository'
      -- reply 를 board 앞에: reply 행 SQL 에 board_id 컬럼이 있어 %board% 에 먼저 걸려 오귀속되던 것 방지.
      WHEN SQL_TEXT LIKE '%reply%'           THEN 'ReplyRepository'
      WHEN SQL_TEXT LIKE '%board%'           THEN 'BoardRepository'
      WHEN SQL_TEXT LIKE '%user%'            THEN 'UserRepository'
      ELSE '(기타)' END)                                                        AS repo,
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'repoMethod=',-1),' ',1),'.',-1),''),
    'Hibernate query')                                                          AS method,
  ROUND(TIMER_WAIT/1e9,3) AS ms,
  ROWS_EXAMINED AS examined, ROWS_SENT AS sent, NO_INDEX_USED AS no_index,
  -- 문장별 메모리(examined 와 직교): mem_bytes 는 작업 메모리 피크, tmp_disk·sort_spill 은 메모리 부족→디스크 스필 신호.
  MAX_TOTAL_MEMORY AS mem_bytes, CREATED_TMP_DISK_TABLES AS tmp_disk, SORT_MERGE_PASSES AS sort_spill,
  EVENT_ID AS event_id
FROM performance_schema.events_statements_history_long
WHERE SQL_TEXT LIKE '/* reqId=%';
ALTER TABLE trace_snapshot
  MODIFY repo VARCHAR(40), MODIFY method VARCHAR(60),
  ADD INDEX(reqId), ADD INDEX(uri), ADD INDEX(repo);
-- uri 정규화: MDC uri 는 핸들러 매핑 후에야 라우트 패턴(GET:/explanation/{id})으로 갈리므로, 매핑 전
--  SQL(인증 필터의 유저 조회)은 원본 경로(GET:/explanation/0M62R7MG)로 귀속돼 요청마다 1회짜리 그룹으로
--  흩어진다. 같은 reqId 의 행들을 그 요청의 패턴 uri({ 포함 버전)로 통일해 한 그룹으로 합친다
--  (패턴 행이 없는 요청=경로변수 없는 uri 는 그대로).
UPDATE trace_snapshot ts
JOIN (SELECT reqId, MAX(uri) AS pat FROM trace_snapshot WHERE uri LIKE '%{%' GROUP BY reqId) p
  ON ts.reqId = p.reqId
SET ts.uri = p.pat;" 2>/dev/null
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb -t -e "
SELECT uri, COUNT(DISTINCT reqId) reqs, ROUND(COUNT(*)/COUNT(DISTINCT reqId),1) q_per_req,
       ROUND(SUM(examined)/COUNT(DISTINCT reqId)) ex_per_req,
       ROUND(MAX(mem_bytes)/1024) max_mem_kb
FROM trace_snapshot GROUP BY uri ORDER BY q_per_req DESC;" 2>/dev/null

# 4) 스크레이프 정착 — 앱이 죽기 전에 Prometheus가 최종 카운터를 긁게 대기(>5s scrape_interval).
#    안 그러면 끝에 몰린 짧은 부하(sched·refresh)의 §① series 가 스크레이프 눈금 사이로 밀려 누락된다.
echo "[$LABEL] Prometheus 스크레이프 정착 대기(10s)"
sleep 10

# 문장 계측 전역 설정 원복 — x100 은 대시보드·인핸스먼트 측정이 계속 쓰는 상주 DB라 스크립트가 남긴 전역 상태를
#  되돌린다(slow log 는 부하 직후 이미 OFF).
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -e "
  UPDATE performance_schema.setup_instruments SET ENABLED='YES', TIMED='YES'
    WHERE NAME IN ('statement/sql/set_option','statement/sql/commit','statement/sql/begin','statement/sql/rollback');" 2>/dev/null

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

# PHASE 가드 — 미설정 시 phase 라벨이 안 붙어(run_level:242) before/after 필터 패널에서 탈락하는 "유령 측정"이
#  된다(무라벨 시리즈만 남아 대시보드에 안 보임). 시작 전에 막아 헛스윕·헛빌드를 방지한다. PHASE=before|after 필수.
case "${PHASE:-}" in
  before|after) ;;
  *) echo "[run.sh] PHASE 미설정/오류 — before/after 필터 패널에 안 잡히는 유령 측정 방지. 예: PHASE=before $0 ${want}" >&2; exit 1 ;;
esac

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
