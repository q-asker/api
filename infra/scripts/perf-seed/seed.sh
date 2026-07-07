#!/usr/bin/env bash
# =============================================================
# perf-seed 실행 래퍼 (FR-011 / SC-007 / research D9)
# 흐름: 환경 가드 → 예상 용량 출력 → seed.sql 실행 → verify.sql 검증
# 대상: 로컬 Docker MySQL 전용 (운영 DB 오염 방지 가드)
#
# 사용:
#   ./seed.sh --scale 10          # 시드(기본 scale=10)
#   ./seed.sh --verify            # 분포 검증만
#   ./seed.sh --cleanup           # seed- 데이터 일괄 삭제
#
# 접속 정보(환경변수, 기본값은 infra/mysql 로컬):
#   MYSQL_HOST(127.0.0.1) MYSQL_PORT(3306) MYSQL_USER(root)
#   MYSQL_PASSWORD MYSQL_DATABASE(qasker)
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SCALE=10
MODE="seed"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --scale)   SCALE="$2"; shift 2 ;;
    --verify)  MODE="verify"; shift ;;
    --cleanup) MODE="cleanup"; shift ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 64 ;;
  esac
done

HOST="${MYSQL_HOST:-127.0.0.1}"
PORT="${MYSQL_PORT:-3306}"
USER="${MYSQL_USER:-root}"
DB="${MYSQL_DATABASE:-qasker}"
PASS="${MYSQL_PASSWORD:-}"

# ── 환경 가드: 허용 호스트가 아니면 즉시 중단 (운영 DB 보호) ──
ALLOWED_HOSTS=("127.0.0.1" "localhost" "q-asker-db" "mysql" "::1")
allowed=0
for h in "${ALLOWED_HOSTS[@]}"; do [[ "$HOST" == "$h" ]] && allowed=1; done
if [[ $allowed -eq 0 ]]; then
  echo "🛑 거부: 호스트 '$HOST'는 허용 목록(로컬 전용)이 아닙니다. 운영 DB에는 시드할 수 없습니다." >&2
  echo "   허용: ${ALLOWED_HOSTS[*]}" >&2
  exit 77
fi

# --default-character-set=utf8mb4: 한글 필러가 이중 인코딩되어 바이트가 부풀지 않도록 강제
MYSQL=(mysql --default-character-set=utf8mb4 -h "$HOST" -P "$PORT" -u "$USER" "$DB")
[[ -n "$PASS" ]] && MYSQL=(mysql --default-character-set=utf8mb4 -h "$HOST" -P "$PORT" -u "$USER" "-p${PASS}" "$DB")

run_sql_file() { "${MYSQL[@]}" < "$1"; }

case "$MODE" in
  cleanup)
    echo "🧹 seed- 데이터 정리 중..."
    run_sql_file "$SCRIPT_DIR/cleanup.sql"
    ;;

  verify)
    echo "🔎 분포 검증 중..."
    OUT="$("${MYSQL[@]}" -t < "$SCRIPT_DIR/verify.sql")"
    echo "$OUT"
    if grep -q "FAIL" <<<"$OUT"; then
      echo "❌ 검증 실패: 분포가 실측 대비 ±10%를 벗어났습니다." >&2
      exit 1
    fi
    echo "✅ 검증 통과 (유형 비율·explanation_content 크기 실측 ±10% 이내)"
    ;;

  seed)
    BASELINE=66684
    TARGET=$(( SCALE * BASELINE ))
    # seed.sql의 6자리 시퀀스 생성기 상한(100만) 초과 방지
    if (( TARGET > 1000000 )); then
      echo "🛑 거부: scale=$SCALE → 목표 ${TARGET}행이 생성기 상한(100만)을 초과합니다. scale ≤ 15로 실행하세요." >&2
      exit 64
    fi
    # 예상 용량: 세트당 평균 ~42KB (실측), scale×66684행 기준
    EST_MB=$(( SCALE * 66684 * 2600 / 1000000 ))   # 문항당 평균 ~2.6KB(본문 기준) 근사
    echo "📦 예상 규모: scale=$SCALE → 문항 ~${TARGET}행, 대략 ${EST_MB}MB (실측 분포 기반)"
    echo "   대상: $USER@$HOST:$PORT/$DB (로컬 전용)"
    echo "▶ 시드 실행..."
    { echo "SET @scale = ${SCALE};"; cat "$SCRIPT_DIR/seed.sql"; } | "${MYSQL[@]}"
    echo "▶ 분포 자동 검증..."
    "$0" --verify
    ;;
esac
