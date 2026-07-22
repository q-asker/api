#!/usr/bin/env bash
# =============================================================
# restore-x1.sh — 마스킹 덤프 "파일"을 x1 컨테이너에 복원 + 정합성 검증 (query-tuning x1 세팅).
#   다운로드와 독립: OCI 다운로드는 download-masked.sh 로 따로 받은 뒤 그 파일을 이 스크립트에 넘긴다.
#   흐름: 파일 확인 → DROP → 복원 → row 카운트 + FK 정합.
# 사용: restore-x1.sh <dump.sql|dump.sql.gz> [--container=local-mysql-x1]
#   예) restore-x1.sh /tmp/qasker-masked-....sql.gz
#       DUMP=$(download-masked.sh --latest); restore-x1.sh "$DUMP"
# 전제: 대상 컨테이너가 provision-level.sh 로 이미 생성돼 있어야 한다(여기선 생성하지 않는다).
# =============================================================
set -uo pipefail
C="local-mysql-x1"; DB="qaskerdb"; DUMP=""
while (( $# > 0 )); do case "$1" in
  --container=*) C="${1#--container=}" ;;
  -h|--help)     grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  --*)           echo "[restore-x1] 알 수 없는 옵션: $1" >&2; exit 1 ;;
  *)             DUMP="$1" ;;
esac; shift; done
[[ -n "$DUMP" ]] || { echo "사용: restore-x1.sh <dump.sql[.gz]> [--container=local-mysql-x1]" >&2; exit 1; }
[[ -f "$DUMP" ]] || { echo "[restore-x1] 덤프 파일 없음: $DUMP" >&2; exit 1; }

# 대상 컨테이너는 provision-level.sh 로 미리 생성돼 있어야 한다(prod config·모니터링 네트워크).
docker inspect "$C" >/dev/null 2>&1 || { echo "[restore-x1] $C 없음 — provision-level.sh 로 먼저 생성" >&2; exit 1; }
[ "$(docker inspect -f '{{.State.Running}}' "$C" 2>/dev/null)" = "true" ] || {
  docker start "$C" >/dev/null
  until docker exec -e MYSQL_PWD=password "$C" mysql -uroot -N -e "SELECT 1" 2>/dev/null | grep -q 1; do sleep 1; done; }

# ── DROP + 복원 ──
echo "[restore-x1] $C ← $DUMP (DROP + load)"
docker exec -i -e MYSQL_PWD=password "$C" mysql -uroot \
  -e "DROP DATABASE IF EXISTS \`$DB\`; CREATE DATABASE \`$DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
case "$DUMP" in
  *.gz) gzip -dc "$DUMP" | docker exec -i -e MYSQL_PWD=password "$C" mysql -uroot "$DB" ;;
  *)    docker exec -i -e MYSQL_PWD=password "$C" mysql -uroot "$DB" < "$DUMP" ;;
esac

# ── 정합성 검증 ──
echo "[restore-x1] 검증 — 테이블별 행수"
docker exec -e MYSQL_PWD=password "$C" mysql -uroot "$DB" -t -e "
SELECT TABLE_NAME, TABLE_ROWS AS approx_rows FROM information_schema.TABLES
WHERE TABLE_SCHEMA='$DB' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_ROWS DESC;"
ORPH=$(docker exec -e MYSQL_PWD=password "$C" mysql -uroot -N "$DB" -e "
SELECT COUNT(*) FROM problem p LEFT JOIN problem_set ps ON p.problem_set_id=ps.id WHERE ps.id IS NULL;" 2>/dev/null || echo NA)
echo "[restore-x1] FK: problem→problem_set 고아 = $ORPH (0 이어야)"
[ "$ORPH" = "0" ] || { echo "⚠️ [restore-x1] FK 정합 실패 — 덤프 확인" >&2; exit 1; }
echo "[restore-x1] done ($C) — seed-scale.sh 로 x10/x100 시딩 가능"
