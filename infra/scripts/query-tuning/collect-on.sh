#!/usr/bin/env bash
# 부하 직전: 분석 컨테이너(3307)에 DB 수집 켜기 + 리셋
#   - slow log(TABLE) 활성, long_query_time=0.1s, 인덱스 미사용 쿼리도 기록
#   - slow_log·digest 테이블 리셋(직전 표본 제거) → 이번 부하만 깨끗이 집계
set -euo pipefail
CONTAINER="${CONTAINER:-local-mysql-prod}"   # 3307 분석 DB

docker exec -i -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb <<'SQL'
SET GLOBAL slow_query_log = 1;
SET GLOBAL log_output = 'TABLE';
SET GLOBAL long_query_time = 0.1;
SET GLOBAL log_queries_not_using_indexes = 1;
TRUNCATE mysql.slow_log;
TRUNCATE performance_schema.events_statements_summary_by_digest;
SQL

echo "[collect-on] slow_query_log=ON (long_query_time=0.1s, log_queries_not_using_indexes=1)"
echo "[collect-on] mysql.slow_log · events_statements_summary_by_digest 리셋 완료"
echo "[collect-on] 부하 후 analyze.sh 로 판정. 끝나면 collect-off.sh 로 원복."
