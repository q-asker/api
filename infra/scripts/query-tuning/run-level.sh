#!/usr/bin/env bash
# 스케일 레벨 1개 부하 실행: 앱을 해당 DB에 붙여 수집→부하→refresh 스캔비용 측정→종료.
# 사용: run-level.sh <PORT> <CONTAINER> <LABEL>
set -euo pipefail
PORT="$1"; CONTAINER="$2"; LABEL="$3"
cd /Users/ohyoungje/Desktop/Project/q-asker/api
JAR="app/build/libs/app-3.2.3.jar"
LOG="/tmp/qasker-lt-$LABEL.log"

echo "════════ 레벨 $LABEL ($CONTAINER:$PORT) ════════"
# 이전 앱 잔재 정리
lsof -ti:8080 2>/dev/null | xargs kill -9 2>/dev/null || true

# 0) mysqld-exporter를 이 레벨 DB로 재지정 (풀스캔율 패널이 이 레벨을 반영하도록)
#    MySQL 컨테이너가 127.0.0.1 바인딩이라 host.docker.internal:$PORT 로는 못 붙는다(loopback 포트는
#    컨테이너에서 안 보임). 같은 모니터링 네트워크에서 <컨테이너명>:3306 으로 내부 접근한다
#    (컨테이너는 provision-level.sh 로 local_local-monitoring 네트워크에 생성돼 있어야 함).
docker rm -f local-mysqld-exporter >/dev/null 2>&1 || true
docker run -d --name local-mysqld-exporter --network local_local-monitoring \
  -e MYSQLD_EXPORTER_PASSWORD=password \
  prom/mysqld-exporter:v0.16.0 --mysqld.address=$CONTAINER:3306 \
  --mysqld.username=root --collect.info_schema.innodb_metrics --collect.perf_schema.tableiowaits >/dev/null
echo "[$LABEL] exporter → $CONTAINER:3306"

# 1) 수집 켜기·리셋 (해당 컨테이너)
CONTAINER="$CONTAINER" ./infra/scripts/query-tuning/collect-on.sh >/dev/null && echo "[$LABEL] collect-on"

# 2) 앱 기동 — datasource를 이 레벨 포트로 override (env가 loadtest.yml보다 우선)
export JASYPT_ENCRYPTOR_PASSWORD="$(grep '^JASYPT_ENCRYPTOR_PASSWORD=' app/gradle.properties | cut -d= -f2-)"
export SPRING_PROFILES_ACTIVE=local,loadtest
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$PORT/qaskerdb"
# 이 레벨의 모든 app 메트릭에 seed 라벨 부여 → 레포 타이밍을 seed축으로 비교 (app-first)
export MANAGEMENT_METRICS_TAGS_SEED="$LABEL"
: > "$LOG"
java -jar "$JAR" >> "$LOG" 2>&1 &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null || true; wait $APP_PID 2>/dev/null || true' EXIT

for i in $(seq 1 45); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://localhost:8080/v3/api-docs || echo 000)
  [ "$code" = "200" ] && { echo "[$LABEL] app up (~$((i*2))s)"; break; }
  kill -0 $APP_PID 2>/dev/null || { echo "[$LABEL] 부팅 실패"; grep -iE "APPLICATION FAILED|Caused by" "$LOG" | head -3; exit 1; }
  sleep 2
done
grep -m1 "jdbc:mysql://127.0.0.1:$PORT" "$LOG" >/dev/null && echo "[$LABEL] datasource=$PORT 확인"

# 3) 부하 — refresh 경합 위주(스케일 신호), read 스윕은 가볍게
export ROUNDS=15 DETAIL_SAMPLE=25 REFRESH_ROUNDS=60 REFRESH_CONC=20 MINT_ROUNDS=300
T0=$(($(date +%s) * 1000))
BASE=http://localhost:8080 ./infra/scripts/query-tuning/loadgen.sh 2>&1 | tail -3
T1=$(($(date +%s) * 1000))

# 3b) Grafana annotation — 이 레벨 구간을 타임라인에 라벨로 표시
curl -s -u admin:admin -H "Content-Type: application/json" -X POST \
  http://localhost:3000/api/annotations \
  -d "{\"time\":$T0,\"timeEnd\":$T1,\"tags\":[\"loadtest\",\"$LABEL\"],\"text\":\"$LABEL — $CONTAINER:$PORT\"}" >/dev/null \
  && echo "[$LABEL] annotation 등록"

# 4) 프로메테우스 스크레이프 여유
sleep 8
p95=$(curl -s "http://localhost:9091/api/v1/query" \
  --data-urlencode 'query=histogram_quantile(0.95, sum by(le)(rate(http_server_requests_seconds_bucket{uri="/auth/refresh"}[2m])))' \
  | jq -r '.data.result[0].value[1] // "n/a"')
echo "[$LABEL] /auth/refresh p95 = ${p95}s"

# 5) refresh_token 스캔비용 (digest)
echo "[$LABEL] refresh_token digest (examined_per_exec = 요청당 스캔 행수):"
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb -t -e "
SELECT COUNT_STAR execs, SUM_ROWS_EXAMINED examined, SUM_ROWS_SENT sent,
       ROUND(SUM_ROWS_EXAMINED/GREATEST(COUNT_STAR,1)) examined_per_exec, SUM_NO_INDEX_USED no_index,
       ROUND(SUM_TIMER_WAIT/GREATEST(COUNT_STAR,1)/1e9, 3) avg_ms
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME='qaskerdb' AND DIGEST_TEXT LIKE '%refresh_token%'
ORDER BY SUM_ROWS_EXAMINED DESC LIMIT 3;" 2>/dev/null

echo "[$LABEL] done"
