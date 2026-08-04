#!/usr/bin/env bash
# 전 테이블 스케일업 시딩 — 대상 볼륨을 fresh 재생성한 뒤 x1 원본 복원 → 원본 행을 (scale-1)번 복제한다.
#   흐름: 볼륨 fresh 재생성(provision-level) → x1 덤프 복원 → 배수 복제. 재실행해도 매번 fresh x<scale>.
#   볼륨 재생성으로 매 시딩마다 AUTO_INCREMENT·시리얼·ibdata 파일 누적을 리셋한다(시리얼 폭증 방지).
#   복제는 제약키(PK/UK/FK)만 새로 생성, 내용은 원본 그대로.
#   전제: x1 소스 컨테이너(기본 local-mysql-x1)에 순수 원본이 있어야 한다. x1 정지 상태면 자동 기동 후 원복.
# 사용: seed-scale.sh [level|container] [scale] [x1_source=local-mysql-x1]
#   무인자:   seed-scale.sh              → x10, x100 을 순차 시딩(각 레벨 자기 재호출, 레벨마다 RAM 가드 적용)
#   레벨 축약: seed-scale.sh x100        → 컨테이너 local-mysql-x100, scale 100 자동
#             seed-scale.sh x10          → local-mysql-x10, scale 10
#   명시:     seed-scale.sh local-mysql-x100 100   /   seed-scale.sh local-mysql-x100 200(=x200 재현)
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"

# 무인자 → x10, x100 순차 시딩(각각 단일 대상 경로로 자기 재호출). x1 은 원본이라 대상 아님.
if [ "$#" -eq 0 ]; then
  for lvl in x10 x100; do
    echo "════════════════ seed-scale: $lvl ════════════════"
    bash "$DIR/seed-scale.sh" "$lvl"
  done
  echo "[seed] all done (x10, x100)"
  exit 0
fi

C="${1:?사용: seed-scale.sh <x100|local-mysql-x100> [scale]}"; SCALE="${2:-}"; X1="${3:-local-mysql-x1}"
# 레벨 축약(x100)·컨테이너명 끝 숫자에서 컨테이너·scale 유도(scale 명시하면 그 값 우선).
case "$C" in
  x[0-9]*)             N="${C#x}"; C="local-mysql-x$N"; SCALE="${SCALE:-$N}" ;;
  local-mysql-x[0-9]*) SCALE="${SCALE:-${C##*-x}}" ;;
esac
[ -n "$SCALE" ] || { echo "[seed] scale 을 못 정함 — 사용: seed-scale.sh <x100|local-mysql-x100> [scale]" >&2; exit 1; }
MY(){ docker exec -i -e MYSQL_PWD=password "$C" mysql --default-character-set=utf8mb4 -uroot qaskerdb; }
MULT=$((SCALE - 1))

wait_ready(){ until docker exec -e MYSQL_PWD=password "$1" mysql -uroot -N -e "SELECT 1" 2>/dev/null | grep -q 1; do sleep 1; done; }
ensure_up(){ [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = "true" ] || { docker start "$1" >/dev/null; wait_ready "$1"; }; }

# x1 스케일 충분성 가드(경고 전용, 구 check-x1-scale.sh 인라인) — x1 행수를 x<SCALE>(기본 ×100)로 투영해
#  FLOOR(기본 10,000) 미만 도메인 테이블을 ⚠️ 로 알린다. information_schema 자동 발견이라 새 테이블도 자동 포함.
#  () 서브셸 본문이라 set/MY 재정의·exit 가 본체에 새지 않는다. 절대 진행을 막지 않는다(항상 종료코드 0).
#  제외: 스케일 대상 아닌 flyway_schema_history(마이그레이션 이력)·trace_snapshot(분석 산출물)·
#        pii_classification(HASH/SAFE 분류 참조 테이블 — 행수 고정, 복제 대상 아님).
check_x1_scale() (
  set -uo pipefail
  C="${1:?container}"; FLOOR="${2:-10000}"; SCALE="${3:-100}"
  MY(){ docker exec -i -e MYSQL_PWD=password "$C" mysql -uroot -N qaskerdb; }
  EXCLUDE=" flyway_schema_history trace_snapshot pii_classification "
  TABLES=$(echo "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='qaskerdb' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;" | MY 2>/dev/null || true)
  if [ -z "$TABLES" ]; then
    echo "[check] $C — 테이블 조회 실패(컨테이너/DB 확인). 경고만이므로 건너뜀."; exit 0
  fi
  echo "[check] $C — x1 → x${SCALE} 투영, FLOOR=${FLOOR} (경고 전용, 자동 발견)"
  printf '%-24s %10s %13s  %s\n' "table" "rows" "x${SCALE}_proj" "status"
  printf '%.0s-' {1..66}; echo
  warn=0
  for t in $TABLES; do
    [[ "$EXCLUDE" == *" $t "* ]] && continue
    n=$(echo "SELECT COUNT(*) FROM \`$t\`;" | MY 2>/dev/null || echo NA)
    [ "$n" = NA ] && continue
    proj=$((n * SCALE))
    if [ "$proj" -ge "$FLOOR" ]; then
      status="OK"
    else
      status="⚠️ FLOOR 미만"; warn=$((warn + 1))
    fi
    printf '%-24s %10s %13s  %s\n' "$t" "$n" "$proj" "$status"
  done
  echo
  if [ "$warn" -gt 0 ]; then
    echo "⚠️  ${warn}개 테이블이 x${SCALE}에서 ${FLOOR} 미만 — 스케일 신호 약함(경고만, 진행엔 영향 없음)."
  else
    echo "✅ 전 도메인 테이블이 x${SCALE}에서 ${FLOOR} 이상."
  fi
  exit 0
)

# ── 0) 초기화: 대상 볼륨 fresh 재생성 → x1 원본 복원 (항상 fresh + 시리얼·파일 누적 리셋) ──
[ "$C" != "$X1" ] || { echo "[seed] x1 소스($X1)를 대상으로 줄 수 없음 — 원본 보호" >&2; exit 1; }
docker inspect "$X1" >/dev/null 2>&1 || { echo "[seed] x1 소스 $X1 없음" >&2; exit 1; }
# 대상 호스트 포트(볼륨 재생성용) — 표준 레벨명에서 유도, 비표준이면 PORT env 필요.
case "$C" in
  *-x1)   PORT="${PORT:-3307}" ;;
  *-x10)  PORT="${PORT:-3308}" ;;
  *-x100) PORT="${PORT:-3309}" ;;
  *)      : "${PORT:?비표준 컨테이너명 — PORT env 로 호스트 포트 지정}" ;;
esac
x1_was_stopped=0
[ "$(docker inspect -f '{{.State.Running}}' "$X1" 2>/dev/null)" = "true" ] || x1_was_stopped=1
ensure_up "$X1"

# 다른 레벨 컨테이너 정지 — 4G 버퍼풀 MySQL 이 동시 상주하면 RAM 압박으로 대상이 OS 에 강제종료(SIGKILL 137)돼
#  시딩이 중단된다(run.sh 와 동일 방어). 대상($C)·x1 소스($X1)는 제외. 한 번에 한 레벨만 띄운다.
for other in local-mysql-x1 local-mysql-x10 local-mysql-x100; do
  [ "$other" != "$C" ] && [ "$other" != "$X1" ] \
    && docker stop "$other" >/dev/null 2>&1 && echo "[seed] $other 정지(RAM 반환)" || true
done

# 볼륨 fresh 재생성: provision-level 이 docker rm -f -v(옛 볼륨 제거) + 새 익명 볼륨 + prod config 로 (재)생성.
#  매 시딩마다 AUTO_INCREMENT/시리얼·ibdata 파일 누적이 리셋된다.
echo "[seed] $C 볼륨 fresh 재생성(provision-level $PORT) — 시리얼·파일 누적 리셋"
bash "$DIR/provision-level.sh" "$C" "$PORT"

echo "[seed] $C ← $X1 복원"
docker exec -i -e MYSQL_PWD=password "$C" mysql -uroot \
  -e "CREATE DATABASE IF NOT EXISTS qaskerdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
docker exec -e MYSQL_PWD=password "$X1" \
  mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF -uroot qaskerdb \
  | docker exec -i -e MYSQL_PWD=password "$C" mysql -uroot qaskerdb
[ "$x1_was_stopped" = 1 ] && { docker stop "$X1" >/dev/null; echo "[seed] $X1 정지(원복)"; }

# ── 1) x1 충분성 가드 (경고 전용) ──
check_x1_scale "$C" || true

# ── 2) 배수 복제 (프로시저 seed_scale: 소형=한 방, problem=copy 루프 청킹) ──
echo "[seed] $C scale=$SCALE — 전 테이블 ×$MULT 복제(seed-scale.sql 프로시저)"
{ echo "SET @scale=$SCALE;"; cat "$DIR/seed-scale.sql"; } | MY

echo "[seed] 검증 — 총량(원본+복제) = 원본 × $SCALE 이어야 함"
MY <<SQL
SELECT 'user' t, COUNT(*) actual FROM user
UNION ALL SELECT 'problem_set', COUNT(*) FROM problem_set
UNION ALL SELECT 'problem', COUNT(*) FROM problem
UNION ALL SELECT 'quiz_history', COUNT(*) FROM quiz_history
UNION ALL SELECT 'refresh_token', COUNT(*) FROM refresh_token
UNION ALL SELECT 'essay_grade_log', COUNT(*) FROM essay_grade_log
UNION ALL SELECT 'board', COUNT(*) FROM board
UNION ALL SELECT 'feedback_board', COUNT(*) FROM feedback_board
UNION ALL SELECT 'quiz_folder', COUNT(*) FROM quiz_folder
UNION ALL SELECT 'reply', COUNT(*) FROM reply
UNION ALL SELECT 'problem_quality_log', COUNT(*) FROM problem_quality_log;
SQL
echo "[seed] done ($C x$SCALE)"
