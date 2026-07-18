#!/usr/bin/env bash
# 부하 후 판정: digest 스캔 랭킹(①) + slow log 태그 귀속(②) + refresh_token 지목
set -euo pipefail
CONTAINER="${CONTAINER:-local-mysql-prod}"
Q() { docker exec -i -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb -t -e "$1"; }

echo "════════ ① digest — 스캔 점수 랭킹 (examined/sent, no_index) ════════"
# examined_per_sent 높을수록 '많이 훑고 적게 반환' = 풀스캔/미싱인덱스 신호
Q "
SELECT LEFT(DIGEST_TEXT, 70)                                   AS query,
       COUNT_STAR                                              AS execs,
       SUM_ROWS_EXAMINED                                       AS examined,
       SUM_ROWS_SENT                                           AS sent,
       ROUND(SUM_ROWS_EXAMINED / GREATEST(SUM_ROWS_SENT,1), 1) AS examined_per_sent,
       SUM_NO_INDEX_USED                                       AS no_index,
       ROUND(SUM_TIMER_WAIT/1e12, 2)                           AS total_sec
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME='qaskerdb' AND DIGEST_TEXT IS NOT NULL
ORDER BY examined_per_sent DESC, examined DESC
LIMIT 15;"

echo
echo "════════ ① digest — refresh_token 지목 ════════"
Q "
SELECT LEFT(DIGEST_TEXT, 90) AS query, COUNT_STAR AS execs,
       SUM_ROWS_EXAMINED AS examined, SUM_ROWS_SENT AS sent, SUM_NO_INDEX_USED AS no_index
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME='qaskerdb' AND DIGEST_TEXT LIKE '%refresh_token%'
ORDER BY SUM_ROWS_EXAMINED DESC;"

echo
echo "════════ ② slow log — 엔드포인트 귀속 (sqlcommenter /* uri= */ 태그) ════════"
# digest는 코멘트를 지우지만 slow log 원문엔 태그가 남는다 → 스캔을 엔드포인트로 귀속
Q "
SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(CONVERT(sql_text USING utf8), 'uri=', -1), ' ', 1) AS uri,
       COUNT(*)                          AS slow_cnt,
       ROUND(AVG(rows_examined))         AS avg_examined,
       ROUND(AVG(TIME_TO_SEC(query_time))*1000, 1) AS avg_ms
FROM mysql.slow_log
WHERE CONVERT(sql_text USING utf8) LIKE '/* reqId=%'
GROUP BY uri
ORDER BY avg_examined DESC;"

echo
echo "════════ ② slow log — FOR UPDATE 풀스캔 상위 ════════"
Q "
SELECT LEFT(CONVERT(sql_text USING utf8), 100) AS sql_text,
       rows_examined,
       ROUND(TIME_TO_SEC(query_time)*1000, 1)  AS ms
FROM mysql.slow_log
WHERE CONVERT(sql_text USING utf8) LIKE '%for update%'
ORDER BY rows_examined DESC
LIMIT 10;"

echo
echo "판정: refresh_token 룩업이 ①에서 examined≫sent·no_index>0 상위 + ②에서 /auth/refresh 태그로 귀속되면 확정."
