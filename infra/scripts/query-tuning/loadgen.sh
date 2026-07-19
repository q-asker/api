#!/usr/bin/env bash
# 쿼리 튜닝 부하 하네스 — 실 엔드포인트 타격(읽기 GET + 실 write + 스케줄러 + refresh 경합).
#  mock 서비스가 write를 save→delete로 자기정리(순증 0)하므로 실 write 엔드포인트를 그대로 때린다.
#  → /local/write·/local/repo-bench 드라이버 제거. 읽기·쓰기 모두 실 URI 로 잡힌다.
# 전제: 앱이 local,loadtest,mock 로 떠 있어야 함(mock 없으면 실 데이터 변경·외부호출[GitHub 이슈]이 발생).
# 하는 일: 토큰 → GET 열거·id 수확 → (읽기+쓰기) xROUNDS → 스케줄러 트리거 → refresh 경합.
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
for p in "${GET_PATHS[@]}"; do [[ "$p" == *"{"* ]] && TEMPLATED+=("$p") || STATIC+=("$p"); done
echo "[enum] static GET=${#STATIC[@]}  templated GET=${#TEMPLATED[@]}"

# ── 실 write 엔드포인트(mock 자기정리, 순증 0). __ID__→수확 id, __SID__→uuid ──
WRITES=(
  "POST|/boards|{\"title\":\"mock\",\"content\":\"mock\"}"
  "PUT|/boards/__ID__|{\"title\":\"mock\",\"content\":\"mock\"}"
  "DELETE|/boards/__ID__|"
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

IDPOOL=$(mktemp); trap 'rm -f "$IDPOOL"' EXIT

harvest() {  # static GET 응답에서 hashid 수확
  for p in "${STATIC[@]}"; do
    curl -s --max-time 10 "${AUTH[@]}" "$BASE$p" | jq -r '[.. | strings] | .[]' 2>/dev/null \
      | grep -aE '^[A-Za-z0-9_-]{8,}$' >> "$IDPOOL" || true
  done
  sort -u "$IDPOOL" -o "$IDPOOL"
}
hit_reads() {  # static + templated GET 1회씩
  for p in "${STATIC[@]}"; do curl -s -o /dev/null --max-time 10 "${AUTH[@]}" "$BASE$p" || true; done
  [[ -s "$IDPOOL" ]] || return 0
  local IDS=(); while IFS= read -r l; do [ -n "$l" ] && IDS+=("$l"); done < <(head -n "$DETAIL_SAMPLE" "$IDPOOL")
  for p in "${TEMPLATED[@]}"; do for id in "${IDS[@]}"; do
    curl -s -o /dev/null --max-time 10 "${AUTH[@]}" "$BASE$(echo "$p" | sed -E 's/\{[^}]+\}/'"$id"'/g')" || true
  done; done
}
hit_writes() {  # $1=대입 id. 실 write 엔드포인트 1회씩(mock 순증 0)
  local id="$1" e m p b args
  for e in "${WRITES[@]}"; do
    IFS='|' read -r m p b <<< "$e"; p="${p//__ID__/$id}"
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

if [[ -n "${DRY_RUN:-}" ]]; then
  echo "── 읽기 GET: static ${STATIC[*]} / templated ${TEMPLATED[*]}"
  echo "── 실 write: ${#WRITES[@]}개 x ROUNDS=$ROUNDS"
  echo "── 스케줄러 x$SCHED_ROUNDS / refresh $REFRESH_CONC x $REFRESH_ROUNDS = $((REFRESH_CONC*REFRESH_ROUNDS))발"
  echo "[dry-run] 종료."; exit 0
fi

harvest
ID=$(head -n1 "$IDPOOL"); [ -z "$ID" ] && ID=mock
echo "[load] 읽기+쓰기 엔드포인트 x$ROUNDS 라운드 (대입 id=$ID)"
for ((i=0; i<ROUNDS; i++)); do hit_reads; hit_writes "$ID"; done
echo "[load] 스케줄러 트리거 x$SCHED_ROUNDS"; sched_burst
echo "[load] refresh 경합 $REFRESH_CONC x $REFRESH_ROUNDS (총 $((REFRESH_CONC*REFRESH_ROUNDS))발)"; refresh_burst
echo "[done]"
