#!/usr/bin/env bash
# MySQL 스케일 레벨 컨테이너를 127.0.0.1 바인딩 + 모니터링 네트워크로 (재)생성.
#  - 127.0.0.1 전용 바인딩: 외부 노출 차단(공개 MySQL 랜섬 스캔 방지). 절대 0.0.0.0 으로 열지 않는다.
#  - local_local-monitoring 네트워크 연결: mysqld-exporter 가 host 포트가 아니라 <컨테이너명>:3306 으로
#    내부 접근하므로, 포트를 loopback 에 묶어도 메트릭 수집이 끊기지 않는다(run-level.sh 참조).
# 사용: provision-level.sh <name> <hostPort> [volume]
#  - volume 지정 시 기존 데이터 보존(x1/x10 재생성), 생략 시 새 볼륨(빈 DB → 복원/시딩 대상, 예: x100).
set -euo pipefail
NAME="${1:?container name}"; PORT="${2:?host port}"; VOL="${3:-}"
NET=local_local-monitoring

docker rm -f "$NAME" 2>/dev/null || true
VOLOPT=(); [ -n "$VOL" ] && VOLOPT=(-v "$VOL:/var/lib/mysql")

# 프로덕션 MySQL 스펙에 맞춘 startup 파라미터(prod SHOW VARIABLES 기준).
#  콜드/워ム 레짐·옵티마이저 플랜을 prod와 정합시키기 위함. 나머지 값(세션 버퍼·optimizer_switch·
#  sql_mode·charset·isolation)은 8.0 기본값이 이미 prod와 일치해 생략.
#  buffer_pool_instances·flush_method·read_io_threads 는 런타임 변경 불가라 여기(startup)서만 잡힌다.
#  주의: 3레벨 모두 4G 풀이라 동시 상주 시 RAM 압박 — 스윕은 한 번에 한 레벨만 띄운다.
PROD_CONF=(
  --innodb_buffer_pool_size=4294967296      # 4 GiB
  --innodb_buffer_pool_instances=4
  --innodb_flush_method=O_DIRECT            # OS 페이지캐시 우회(콜드 측정 타당성)
  --innodb_read_io_threads=1
  --innodb_write_io_threads=4
  --innodb_redo_log_capacity=2147483648     # 2 GiB
  --innodb_io_capacity=625
  --innodb_io_capacity_max=10000
  --innodb_flush_neighbors=0
)
docker run -d --name "$NAME" --network "$NET" \
  -p "127.0.0.1:$PORT:3306" "${VOLOPT[@]}" \
  -e MYSQL_ROOT_PASSWORD=password mysql:8.0 "${PROD_CONF[@]}" >/dev/null

echo "[provision] $NAME → 127.0.0.1:$PORT (net=$NET, vol=${VOL:-new})"
echo -n "[provision] 기동 대기"
until docker exec -e MYSQL_PWD=password "$NAME" mysql -uroot -N -e "SELECT 1" 2>/dev/null | grep -q 1; do
  echo -n "."; sleep 1
done
echo " ready"
