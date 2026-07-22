#!/usr/bin/env bash
# 전 테이블 스케일업 시딩 — 대상 DB를 x1 원본으로 초기화한 뒤 원본 행을 (scale-1)번 복제한다.
#   흐름: DROP DATABASE → x1 덤프 복원 → 배수 복제. 재실행해도 매번 fresh x<scale>(누적·중복키 없음).
#   복제는 제약키(PK/UK/FK)만 새로 생성, 내용은 원본 그대로.
#   전제: x1 소스 컨테이너(기본 local-mysql-x1)에 순수 원본이 있어야 한다. x1 정지 상태면 자동 기동 후 원복.
# 사용: seed-scale.sh <container> <scale> [x1_source=local-mysql-x1]
#   예) seed-scale.sh local-mysql-x100 100   /   seed-scale.sh local-mysql-x10 10
set -euo pipefail
C="${1:?container}"; SCALE="${2:?scale}"; X1="${3:-local-mysql-x1}"
DIR="$(cd "$(dirname "$0")" && pwd)"
MY(){ docker exec -i -e MYSQL_PWD=password "$C" mysql --default-character-set=utf8mb4 -uroot qaskerdb; }
MULT=$((SCALE - 1))

wait_ready(){ until docker exec -e MYSQL_PWD=password "$1" mysql -uroot -N -e "SELECT 1" 2>/dev/null | grep -q 1; do sleep 1; done; }
ensure_up(){ [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = "true" ] || { docker start "$1" >/dev/null; wait_ready "$1"; }; }

# ── 0) 초기화: 대상을 x1 원본으로 리셋 (항상 fresh — "날리고 시딩") ──
[ "$C" != "$X1" ] || { echo "[seed] x1 소스($X1)를 대상으로 줄 수 없음 — 원본 보호" >&2; exit 1; }
docker inspect "$C"  >/dev/null 2>&1 || { echo "[seed] $C 없음 — provision-level.sh 로 먼저 생성" >&2; exit 1; }
docker inspect "$X1" >/dev/null 2>&1 || { echo "[seed] x1 소스 $X1 없음" >&2; exit 1; }
x1_was_stopped=0
[ "$(docker inspect -f '{{.State.Running}}' "$X1" 2>/dev/null)" = "true" ] || x1_was_stopped=1
ensure_up "$X1"; ensure_up "$C"

echo "[seed] $C ← $X1 초기화(DROP + x1 복원)"
docker exec -i -e MYSQL_PWD=password "$C" mysql -uroot -e "DROP DATABASE IF EXISTS qaskerdb; CREATE DATABASE qaskerdb;"
docker exec -e MYSQL_PWD=password "$X1" \
  mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF -uroot qaskerdb \
  | docker exec -i -e MYSQL_PWD=password "$C" mysql -uroot qaskerdb
[ "$x1_was_stopped" = 1 ] && { docker stop "$X1" >/dev/null; echo "[seed] $X1 정지(원복)"; }

# ── 1) x1 충분성 가드 (경고 전용) ──
bash "$DIR/check-x1-scale.sh" "$C" || true

# ── 2) 배수 복제 ──
echo "[seed] $C scale=$SCALE — 작은 테이블 복제(원본 × $MULT)"
{ echo "SET @scale=$SCALE;"; cat "$DIR/seed-scale-small.sql"; } | MY

echo "[seed] problem 복제 — copy 1..$MULT (copy 당 원본 문항 전량)"
k=1
while [ "$k" -le "$MULT" ]; do
  { echo "SET @k=$k;"; cat "$DIR/seed-scale-problem.sql"; } | MY
  echo "  problem copy $k done"
  k=$((k + 1))
done

echo "[seed] 검증 — 총량(원본+복제) = 원본 × $SCALE 이어야 함"
MY <<SQL
SELECT 'user' t, COUNT(*) actual FROM user
UNION ALL SELECT 'problem_set', COUNT(*) FROM problem_set
UNION ALL SELECT 'problem', COUNT(*) FROM problem
UNION ALL SELECT 'quiz_history', COUNT(*) FROM quiz_history
UNION ALL SELECT 'refresh_token', COUNT(*) FROM refresh_token
UNION ALL SELECT 'essay_grade_log', COUNT(*) FROM essay_grade_log
UNION ALL SELECT 'board', COUNT(*) FROM board
UNION ALL SELECT 'feedback_board', COUNT(*) FROM feedback_board;
SQL
echo "[seed] done ($C x$SCALE)"
