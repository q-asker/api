#!/usr/bin/env bash
# =============================================================
# Grafana before/after 부하 구간 annotation(region) 생성 — Q3/Q10 (FR-013, SC-008)
# 대상 대시보드에 시간 구간을 마킹해 전/후를 나란히 비교한다.
#
# 사용:
#   annotate.sh <before|after> <start_epoch_s> <end_epoch_s> ["설명"]
# 예:
#   S=$(date +%s); k6 run load.js; E=$(date +%s); annotate.sh before "$S" "$E" "read 1000 TPS cold"
#
# 조절 env: GRAFANA_URL(기본 http://localhost:3000), GRAFANA_AUTH(기본 admin:admin),
#           GRAFANA_DASHBOARD_UID(기본 qasker-enh-ba)
# =============================================================
set -euo pipefail

GRAFANA="${GRAFANA_URL:-http://localhost:3000}"
AUTH="${GRAFANA_AUTH:-admin:admin}"
DASH_UID="${GRAFANA_DASHBOARD_UID:-qasker-enh-ba}"

tag="${1:?before|after 필요}"
start_ms=$(( ${2%.*} * 1000 ))
end_ms=$(( ${3%.*} * 1000 ))
text="${4:-$tag load}"

curl -s -u "$AUTH" -H "Content-Type: application/json" -X POST "$GRAFANA/api/annotations" \
  -d "{\"dashboardUID\":\"$DASH_UID\",\"time\":$start_ms,\"timeEnd\":$end_ms,\"tags\":[\"$tag\",\"quiz-read-opt\"],\"text\":\"$text\"}" \
  | python3 -c "import sys,json;r=json.load(sys.stdin);print('annotation:',r.get('message','?'),'| id',r.get('id','none'))"
