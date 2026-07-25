#!/usr/bin/env bash
# =============================================================
# restore-x1.sh — 마스킹 덤프 "파일"을 x1 컨테이너에 복원 + 정합성 검증 (query-tuning x1 세팅).
#   다운로드와 독립: OCI 다운로드는 download-masked.sh 로 따로 받은 뒤 그 파일을 이 스크립트에 넘긴다.
#   흐름: 파일 확인 → DROP → 복원 → row 카운트 + FK 정합 → x1 베이스 top-up(소형 테이블 100행) → masked 덤프 삭제(로컬 파일 + 버킷 객체).
#   버킷 삭제: 로컬 파일명(qasker-masked-<시각>.sql.gz)에서 masked/YYYY/MM/DD/ 키를 유도해
#             .sql.gz + .sha256 을 지운다. env: BUCKET(기본 qasker-mysql-backup)·OCI_PROFILE(기본 DEFAULT).
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

# ── x1 베이스 top-up: FLOOR 미달 소형 테이블을 100행으로 맞춤(부족분만) ──
# 이 100행 base 가 seed-scale ×배수의 씨앗 → x100 에서 100×100=10,000(FLOOR 충족).
DIR="$(cd "$(dirname "$0")" && pwd)"
echo "[restore-x1] x1 베이스 top-up(board·feedback_board·quiz_folder·reply → 100행)"
{ echo "SET @target=100;"; cat "$DIR/seed-x1-base.sql"; } \
  | docker exec -i -e MYSQL_PWD=password "$C" mysql --default-character-set=utf8mb4 -uroot "$DB" \
  || { echo "⚠️ [restore-x1] x1 베이스 top-up 실패" >&2; exit 1; }

# ── masked 덤프 정리 (복원·검증 성공 후 로컬 파일 제거) ──
rm -f "$DUMP" && echo "[restore-x1] masked 덤프 삭제됨(로컬): $DUMP"

# ── 버킷의 masked 객체도 삭제 (파일명에서 masked/YYYY/MM/DD/ 키 유도) ──
BUCKET="${BUCKET:-qasker-mysql-backup}"; OCI_PROFILE="${OCI_PROFILE:-DEFAULT}"
BN=$(basename "$DUMP"); TS=${BN#qasker-masked-}
if [[ "$BN" == qasker-masked-* && "$TS" =~ ^([0-9]{4})([0-9]{2})([0-9]{2})T ]]; then
  KEY="masked/${BASH_REMATCH[1]}/${BASH_REMATCH[2]}/${BASH_REMATCH[3]}/$BN"
  command -v oci >/dev/null || { echo "[restore-x1] oci CLI 없음 — 버킷 삭제 불가" >&2; exit 1; }
  echo "[restore-x1] 버킷 객체 삭제: $BUCKET/$KEY (+ .sha256, profile=$OCI_PROFILE)"
  oci --profile "$OCI_PROFILE" os object delete -bn "$BUCKET" --name "$KEY" --force \
    || { echo "⚠️ [restore-x1] 버킷 삭제 실패: $KEY" >&2; exit 1; }
  oci --profile "$OCI_PROFILE" os object delete -bn "$BUCKET" --name "${KEY%.sql.gz}.sha256" --force \
    || echo "⚠️ [restore-x1] .sha256 삭제 실패(무시): ${KEY%.sql.gz}.sha256" >&2
else
  echo "[restore-x1] $BN 은 qasker-masked-<시각> 패턴 아님 — 버킷 삭제 건너뜀(로컬 생성 덤프로 판단)"
fi
echo "[restore-x1] done ($C) — seed-scale.sh 로 x10/x100 시딩 가능"
