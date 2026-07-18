#!/usr/bin/env bash
# 분석 후 원복: slow log 끄기 (기본값 복귀). 수집한 slow_log·digest 데이터는 남겨둔다(재분석용).
set -euo pipefail
CONTAINER="${CONTAINER:-local-mysql-prod}"

docker exec -i -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb <<'SQL'
SET GLOBAL slow_query_log = 0;
SET GLOBAL long_query_time = 10;
SET GLOBAL log_queries_not_using_indexes = 0;
SQL

echo "[collect-off] slow_query_log=OFF, long_query_time=10s 원복 (수집 데이터는 보존)"
