#!/usr/bin/env bash
# 3레벨(x1/x10/x100) 순차 스윕 — run-level 을 레벨별 올바른 인자로 호출.
#  레벨→포트→컨테이너 매핑을 여기 박아 손 루프(단어분리 실수)를 없앤다.
#  부하 파라미터(ROUNDS 등)는 env 로 넘기면 각 run-level 이 읽는다.
#  예: ROUNDS=50 bash run-all.sh   /   bash run-all.sh x100   (특정 레벨만)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"

# 레벨 정의: "LABEL PORT CONTAINER"
LEVELS=(
  "x1   3308 local-mysql-orig"
  "x10  3309 local-mysql-x10"
  "x100 3307 local-mysql-prod"
)

want="${1:-all}"   # all | x1 | x10 | x100
for spec in "${LEVELS[@]}"; do
  read -r label port container <<< "$spec"
  [ "$want" != "all" ] && [ "$want" != "$label" ] && continue
  echo "════════════════ run-all: $label ════════════════"
  bash "$HERE/run-level.sh" "$port" "$container" "$label"
  # 중간 레벨(x1·x10)은 정지해 RAM 반환(한 번에 한 레벨). x100은 대시보드가 3307을 읽으므로 유지.
  if [ "$label" != "x100" ]; then
    docker stop "$container" >/dev/null 2>&1 && echo "[run-all] $container 정지(RAM 반환)"
  fi
done
echo "[run-all] 완료 (${want})"
