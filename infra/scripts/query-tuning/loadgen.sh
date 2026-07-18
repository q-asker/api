#!/usr/bin/env bash
# 쿼리 튜닝 부하 하네스 — /v3/api-docs 동적 열거 기반
#
# 전제(앱 실행 조건, IntelliJ Run Config):
#   SPRING_PROFILES_ACTIVE=local,loadtest
# loadtest 프로파일(config/loadtest.yml)이 datasource→3307·레이트리밋 off·계측 빈 활성을 한 번에 처리한다.
# (datasource·레이트리밋을 env로 따로 줄 필요 없음.)
#
# 하는 일: 토큰 발급 → GET 엔드포인트 동적 열거 → static 스윕+id 수확 →
#          templated GET에 수확 id 대입 스윕 → /auth/refresh 동시성 경합(풀스캔 락).
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
USER_ID="${USER_ID:-h_e9887d1d5b31f89c3101b5732df92f4c}"  # DB 실측: problem_set 274·history 268
ROUNDS="${ROUNDS:-20}"                 # read 스윕 반복
DETAIL_SAMPLE="${DETAIL_SAMPLE:-30}"   # templated GET당 대입할 id 표본 수
REFRESH_CONC="${REFRESH_CONC:-20}"     # refresh 동시 요청 수
REFRESH_ROUNDS="${REFRESH_ROUNDS:-50}" # refresh 버스트 라운드

command -v jq >/dev/null || { echo "jq 필요" >&2; exit 1; }

# ── 1) 토큰 ──
TOKEN=$(curl -s "$BASE/local/token?userId=$USER_ID")
case "$TOKEN" in
  unknown*|"") echo "[mint] 토큰 발급 실패: $TOKEN" >&2; exit 1 ;;
esac
AUTH=(-H "Authorization: Bearer $TOKEN")
echo "[mint] token ok (${#TOKEN} chars)"

# ── 레이트리밋 프리플라이트: refresh 1발이 429면 경고 (local 프로파일이면 꺼져 있어야 정상) ──
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/auth/refresh" -b "refresh_token=preflight")
[[ "$code" == "429" ]] && echo "[warn] /auth/refresh=429 — 레이트리밋 켜짐. local 프로파일로 기동됐는지 확인." >&2

# ── 2) 엔드포인트 동적 열거 (GET만, 관리·계측·생성계열 제외) ──
APIDOC=$(curl -s "$BASE/v3/api-docs")
GET_PATHS=()
while IFS= read -r line; do [ -n "$line" ] && GET_PATHS+=("$line"); done < <(
  echo "$APIDOC" | jq -r '.paths | to_entries[] | select(.value.get != null) | .key' \
  | grep -vE '/(admin|actuator|local)(/|$)|api-docs|swagger|generation|stream')

STATIC=(); TEMPLATED=()
for p in "${GET_PATHS[@]}"; do
  if [[ "$p" == *"{"* ]]; then TEMPLATED+=("$p"); else STATIC+=("$p"); fi
done
echo "[enum] static GET=${#STATIC[@]}  templated GET=${#TEMPLATED[@]}"

# ── DRY_RUN: 실제 부하 없이 때릴 계획만 출력하고 종료 ──
if [[ -n "${DRY_RUN:-}" ]]; then
  echo
  echo "── static GET (id 수확용, 그대로 호출) ──"
  printf '  GET %s\n' "${STATIC[@]}"
  echo "── templated GET (수확 id를 {..}에 대입, 표본 ${DETAIL_SAMPLE}개씩) ──"
  printf '  GET %s\n' "${TEMPLATED[@]}"
  echo "── refresh 경합 ──"
  echo "  POST /auth/refresh  (가짜 쿠키, 동시 ${REFRESH_CONC} x ${REFRESH_ROUNDS}라운드 = $((REFRESH_CONC*REFRESH_ROUNDS))발)"
  echo
  echo "[dry-run] 실제 부하 없이 종료. 실행하려면 DRY_RUN 없이 재실행."
  exit 0
fi

IDPOOL=$(mktemp)
trap 'rm -f "$IDPOOL"' EXIT

# ── 3) static GET 스윕 + id 수확 ──
sweep_static() {
  for p in "${STATIC[@]}"; do
    body=$(curl -s --max-time 10 "${AUTH[@]}" "$BASE$p" || true)
    echo "$body" | jq -r '[.. | strings] | .[]' 2>/dev/null \
      | grep -aE '^[A-Za-z0-9_-]{8,}$' >> "$IDPOOL" || true
  done
  sort -u "$IDPOOL" -o "$IDPOOL"
}

# ── 4) templated GET — 수확 id를 {..}에 대입 (표본 제한) ──
sweep_templated() {
  [[ -s "$IDPOOL" ]] || return 0
  IDS=()
  while IFS= read -r line; do [ -n "$line" ] && IDS+=("$line"); done < <(head -n "$DETAIL_SAMPLE" "$IDPOOL")
  for p in "${TEMPLATED[@]}"; do
    for id in "${IDS[@]}"; do
      url="$BASE$(echo "$p" | sed -E 's/\{[^}]+\}/'"$id"'/g')"
      curl -s -o /dev/null --max-time 10 "${AUTH[@]}" "$url" || true
    done
  done
}

# ── 5) refresh 경합 — 가짜 쿠키로 동시 요청 (매번 20,589행 FOR UPDATE 풀스캔 후 miss) ──
refresh_burst() {
  for ((r=0; r<REFRESH_ROUNDS; r++)); do
    for ((c=0; c<REFRESH_CONC; c++)); do
      curl -s -o /dev/null --max-time 10 -X POST "$BASE/auth/refresh" -b "refresh_token=bogus-$RANDOM-$r-$c" &
    done
    wait
  done
}

# ── 6) write 버스트 — mock 쓰기 드라이버로 각 레포 save/delete 동시 반복 (외부 비용 0) ──
WRITE_CONC="${WRITE_CONC:-20}"
WRITE_ROUNDS="${WRITE_ROUNDS:-60}"
write_burst() {
  for ((r=0; r<WRITE_ROUNDS; r++)); do
    for ((c=0; c<WRITE_CONC; c++)); do
      curl -s -o /dev/null --max-time 10 -X POST "$BASE/local/write" &
    done
    wait
  done
}

echo "[load] write 버스트 conc=$WRITE_CONC x rounds=$WRITE_ROUNDS (총 $((WRITE_CONC*WRITE_ROUNDS))발, POST /local/write)"
# 첫 1발은 응답 확인(제약 실패 진단)
echo "  driver: $(curl -s --max-time 10 -X POST "$BASE/local/write")"
write_burst

# ── 7) repo-bench — read 레포 메서드를 직접 N번 호출 (균일 수렴, 스케줄러 쿼리 포함) ──
# 대시보드는 레포 메서드 타이밍만 보므로 API 경로 대신 레포를 직접 구동 → 모든 read가 정확히 N표본.
BENCH_N="${BENCH_N:-100}"
echo "[load] repo-bench — 각 read 레포 메서드 x$BENCH_N 직접 호출 (스케줄러 쿼리 포함)"
echo "  bench: $(curl -s --max-time 300 -X POST "$BASE/local/repo-bench?n=$BENCH_N")"
echo "[load] refresh 경합 conc=$REFRESH_CONC x rounds=$REFRESH_ROUNDS (총 $((REFRESH_CONC*REFRESH_ROUNDS))발)"
refresh_burst
echo "[done]"
