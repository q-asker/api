#!/usr/bin/env bash
# =============================================================
# x1 스케일 충분성 가드 (경고 전용) — x1 행수를 x<SCALE>(기본 ×100)로 투영해 FLOOR(기본 10,000) 미만이면 ⚠️.
#   목적: x1 준비/스케일 시점에 "스케일해도 신호가 약할" 도메인 테이블을 경고한다.
#   자동 발견: information_schema 로 전 BASE 테이블을 훑는다 → 앞으로 새 도메인 테이블이 추가돼도
#             이 스크립트를 안 고쳐도 자동으로 검사 대상에 포함된다(도메인 확장 대비).
#   경고만: 절대 막지 않는다(항상 종료코드 0). 진행/파이프라인에 영향 없음.
#   제외: 스케일 대상이 아닌 것 — flyway_schema_history(마이그레이션 이력), trace_snapshot(분석 산출물).
# 사용: check-x1-scale.sh <container> [floor=10000] [scale=100]
#   예) check-x1-scale.sh local-mysql-x1
# =============================================================
set -uo pipefail
C="${1:?container}"; FLOOR="${2:-10000}"; SCALE="${3:-100}"
MY(){ docker exec -i -e MYSQL_PWD=password "$C" mysql -uroot -N qaskerdb; }

# 도메인 아님(스케일 대상 아님) — 자동 발견에서 제외
EXCLUDE=" flyway_schema_history trace_snapshot "

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
