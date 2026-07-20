#!/usr/bin/env bash
# 쿼리 튜닝 부하 하네스 — 실 엔드포인트 타격(읽기 GET + 실 write + 스케줄러 + refresh + SSE구독 + 로그아웃).
#  mock 서비스가 write를 save→delete로 자기정리(순증 0)하므로 실 write 엔드포인트를 그대로 때린다.
#  → /local/write·/local/repo-bench 드라이버 제거. 읽기·쓰기 모두 실 URI 로 잡힌다.
#  커버리지: admin(권한)·/local(하네스 인프라)·/upload-doc(multipart+외부IO, DB쿼리 없음)·/auth/test(레포 없음)를
#  제외한 모든 엔드포인트가 요청된다. SSE 생성구독·로그아웃은 세션/토큰 미매칭이라 net 0로 조회 경로만 태운다.
# 전제: 앱이 local,loadtest,mock 로 떠 있어야 함(mock 없으면 실 데이터 변경·외부호출[GitHub 이슈]이 발생).
# 하는 일: 토큰 → GET 열거·id 수확 → (읽기+쓰기) xROUNDS → 스케줄러 → refresh 경합 → SSE 생성구독 → 로그아웃.
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
USER_ID="${USER_ID:-h_e9887d1d5b31f89c3101b5732df92f4c}"
ROUNDS="${ROUNDS:-10}"                  # 읽기+쓰기 엔드포인트 반복(각 엔드포인트 호출 횟수)
DETAIL_SAMPLE="${DETAIL_SAMPLE:-25}"    # templated GET당 대입 id 표본
SCHED_ROUNDS="${SCHED_ROUNDS:-10}"      # 스케줄러 트리거 반복
REFRESH_CONC="${REFRESH_CONC:-20}"      # refresh 동시 요청 수
REFRESH_ROUNDS="${REFRESH_ROUNDS:-50}"  # refresh 버스트 라운드
command -v jq >/dev/null || { echo "jq 필요" >&2; exit 1; }

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

# ── 실 write 엔드포인트(mock 자기정리, 순증 0). __ID__→수확 id, __SID__→uuid ──
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
