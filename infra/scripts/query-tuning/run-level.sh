#!/usr/bin/env bash
# 스케일 레벨 1개 부하 실행: 앱을 해당 DB에 붙여 수집→부하→refresh 스캔비용 측정→종료.
# 사용: run-level.sh <PORT> <CONTAINER> <LABEL>
set -euo pipefail
PORT="$1"; CONTAINER="$2"; LABEL="$3"
cd /Users/ohyoungje/Desktop/Project/q-asker/api
# 최신 소스로 bootJar 재빌드 — build/libs 에 남은 이전(예: enhancement 하네스) jar 를 주워
#  오염된 측정을 하지 않도록 한다. quiz-set-impl clean 은 이전 인핸스먼트 모드 클래스 잔재 제거용.
#  run-all 스윕은 앞단에서 한 번만 빌드하고 LT_SKIP_BUILD=1 로 레벨별 중복 빌드를 건너뛴다.
if [ -z "${LT_SKIP_BUILD:-}" ]; then
  echo "[$LABEL] bootJar 재빌드"
  ./gradlew :quiz-set-impl:clean :app:bootJar -q
fi
# -plain.jar(Main-Class 없는 비실행 jar)는 제외 — 실행 가능한 bootJar 산출물만 고른다.
JAR="$(ls -t app/build/libs/app-*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -1)"
LOG="/tmp/qasker-lt-$LABEL.log"

echo "════════ 레벨 $LABEL ($CONTAINER:$PORT) ════════"
# 레벨 DB 기동 보장 — 정지 상태면 start 후 준비 대기(손으로 껐다 켤 필요 없음).
#  컨테이너 자체가 없으면 재시딩(수백만 행)이 필요하므로 자동 생성하지 않고 안내 후 중단.
if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
  echo "[$LABEL] 컨테이너 $CONTAINER 없음 — provision-level.sh + seed-scale.sh 로 먼저 생성·시딩하세요(README 1·2)"; exit 1
fi
if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" != "true" ]; then
  docker start "$CONTAINER" >/dev/null
  echo -n "[$LABEL] $CONTAINER 기동 대기"
  until docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -N -e "SELECT 1" 2>/dev/null | grep -q 1; do
    echo -n "."; sleep 1
  done
  echo " ready"
fi
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

# 1) 앱 기동 — datasource를 이 레벨 포트로 override (env가 loadtest.yml보다 우선)
export JASYPT_ENCRYPTOR_PASSWORD="$(grep '^JASYPT_ENCRYPTOR_PASSWORD=' app/gradle.properties | cut -d= -f2-)"
export SPRING_PROFILES_ACTIVE=local,loadtest,mock  # mock: loadgen이 실 write 엔드포인트를 순증 0으로 태움
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$PORT/qaskerdb"
# 이 레벨의 모든 app 메트릭에 seed 라벨 부여 → 레포 타이밍을 seed축으로 비교 (app-first)
export MANAGEMENT_METRICS_TAGS_SEED="$LABEL"
: > "$LOG"
java -jar "$JAR" >> "$LOG" 2>&1 &
APP_PID=$!
# SIGKILL 로 즉시 종료 — SIGTERM(graceful)이면 loadgen이 열어둔 SSE 요청이 끝나길 기다려
#  app-common.yml 의 timeout-per-shutdown-phase(300s)를 매 레벨 다 채우고, 그동안 wait 가 블록돼
#  run-all 이 다음 레벨로 못 넘어간다. 일회용 부하 JVM + 일회용 분석 DB(mock 쓰기 순증 0)라 강제종료 안전.
trap 'kill -9 $APP_PID 2>/dev/null || true; wait $APP_PID 2>/dev/null || true' EXIT

for i in $(seq 1 45); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://localhost:8080/v3/api-docs || echo 000)
  [ "$code" = "200" ] && { echo "[$LABEL] app up (~$((i*2))s)"; break; }
  kill -0 $APP_PID 2>/dev/null || { echo "[$LABEL] 부팅 실패"; grep -iE "APPLICATION FAILED|Caused by" "$LOG" | head -3; exit 1; }
  sleep 2
done
grep -m1 "jdbc:mysql://127.0.0.1:$PORT" "$LOG" >/dev/null && echo "[$LABEL] datasource=$PORT 확인"

# 1b) slow log 수집 켜기·리셋 (대시보드 '부록 — 슬로우 쿼리 로그' 패널이 mysql.slow_log 를 읽는다).
#     >0.1s + 무인덱스 쿼리를 이번 실행분만 기록. 분석 컨테이너는 일회용이라 원복 불필요.
docker exec -i -e MYSQL_PWD=password "$CONTAINER" mysql -uroot >/dev/null 2>&1 <<'SQL'
SET GLOBAL slow_query_log = 1;
SET GLOBAL log_output = 'TABLE';
SET GLOBAL long_query_time = 0.1;
SET GLOBAL log_queries_not_using_indexes = 1;
TRUNCATE mysql.slow_log;
SQL
echo "[$LABEL] slow log 수집 ON (0.1s) + 리셋"

# 2) 무거운 부하 — loadgen(실 엔드포인트) → Micrometer seed 라벨 → §① 스케일 지연곡선
export ROUNDS="${ROUNDS:-50}" DETAIL_SAMPLE="${DETAIL_SAMPLE:-25}" REFRESH_ROUNDS="${REFRESH_ROUNDS:-60}" REFRESH_CONC="${REFRESH_CONC:-20}" SCHED_ROUNDS="${SCHED_ROUNDS:-10}"
T0=$(($(date +%s) * 1000))
BASE=http://localhost:8080 ./infra/scripts/query-tuning/loadgen.sh 2>&1 | tail -3
T1=$(($(date +%s) * 1000))

# 2b) Grafana annotation — 이 레벨 구간을 타임라인에 라벨로 표시
curl -s -u admin:admin -H "Content-Type: application/json" -X POST \
  http://localhost:3000/api/annotations \
  -d "{\"time\":$T0,\"timeEnd\":$T1,\"tags\":[\"loadtest\",\"$LABEL\"],\"text\":\"$LABEL — $CONTAINER:$PORT\"}" >/dev/null \
  && echo "[$LABEL] annotation 등록"

# 3) 요청 귀속 스냅샷 — 가벼운 loadgen 패스 + trace_snapshot.
#    스케일 패스(위 무거운 loadgen)와 볼륨이 달라 별 패스로 뜬다: history_long(10k 링버퍼)에 맞춰 가볍게 태운다.
#    → 이 레벨 DB 의 uri·repo.method별 examined 가 trace_snapshot 에 남아 §②③를 채운다(레벨마다 자기 DB에 저장).
echo "[$LABEL] 요청 귀속 트레이스(가벼운 패스):"
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot -e "
  UPDATE performance_schema.setup_consumers SET ENABLED='YES' WHERE NAME='events_statements_history_long';
  TRUNCATE performance_schema.events_statements_history_long;" 2>/dev/null
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb -e "DROP TABLE IF EXISTS trace_snapshot;" 2>/dev/null
ROUNDS=3 DETAIL_SAMPLE=10 SCHED_ROUNDS=5 REFRESH_CONC=10 REFRESH_ROUNDS=8 \
  BASE=http://localhost:8080 ./infra/scripts/query-tuning/loadgen.sh 2>&1 | tail -4
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb -e "
CREATE TABLE trace_snapshot AS
SELECT
  CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'reqId=',-1),' ',1) AS CHAR(16)) AS reqId,
  CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'uri=',-1),' ',1) AS CHAR(80))   AS uri,
  LEFT(TRIM(SUBSTRING_INDEX(SQL_TEXT,'*/',-1)),150)                              AS sql_stripped,
  -- repo·method는 앱이 심은 repoMethod= 주석에서 파싱(repoMethod=BoardRepository.save → repo=BoardRepository, method=save).
  --  레포 메서드를 안 거친 SQL(lazy-load·dirty-check UPDATE 등)은 주석이 비므로 테이블명으로 repo 복원 + method='Hibernate query'.
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'repoMethod=',-1),' ',1),'.', 1),''),
    CASE
      WHEN SQL_TEXT LIKE '%refresh_token%'   THEN 'RefreshTokenRepository'
      WHEN SQL_TEXT LIKE '%quiz_history%'    THEN 'QuizHistoryRepository'
      WHEN SQL_TEXT LIKE '%essay_grade_log%' THEN 'EssayGradeLogRepository'
      WHEN SQL_TEXT LIKE '%feedback_board%'  THEN 'FeedbackBoardRepository'
      -- 테이블명 뒤 경계(공백/괄호)로 앵커링한다. 컬럼명 problem_set_id 가 %problem_set% 에 걸려
      --  problem 테이블 SQL(lazy explanation 로딩 등)이 ProblemSet 으로 오귀속되던 버그 방지.
      WHEN SQL_TEXT LIKE '%problem\_set %' OR SQL_TEXT LIKE '%problem\_set(%' THEN 'ProblemSetRepository'
      WHEN SQL_TEXT LIKE '%problem %'      OR SQL_TEXT LIKE '%problem(%'      THEN 'ProblemRepository'
      WHEN SQL_TEXT LIKE '%board%'           THEN 'BoardRepository'
      WHEN SQL_TEXT LIKE '%reply%'           THEN 'ReplyRepository'
      WHEN SQL_TEXT LIKE '%user%'            THEN 'UserRepository'
      ELSE '(기타)' END)                                                        AS repo,
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'repoMethod=',-1),' ',1),'.',-1),''),
    'Hibernate query')                                                          AS method,
  ROUND(TIMER_WAIT/1e9,3) AS ms,
  ROWS_EXAMINED AS examined, ROWS_SENT AS sent, NO_INDEX_USED AS no_index, EVENT_ID AS event_id
FROM performance_schema.events_statements_history_long
WHERE SQL_TEXT LIKE '/* reqId=%';
ALTER TABLE trace_snapshot
  MODIFY repo VARCHAR(40), MODIFY method VARCHAR(60),
  ADD INDEX(reqId), ADD INDEX(uri), ADD INDEX(repo);" 2>/dev/null
docker exec -e MYSQL_PWD=password "$CONTAINER" mysql -uroot qaskerdb -t -e "
SELECT uri, COUNT(DISTINCT reqId) reqs, ROUND(COUNT(*)/COUNT(DISTINCT reqId),1) q_per_req,
       ROUND(SUM(examined)/COUNT(DISTINCT reqId)) ex_per_req
FROM trace_snapshot GROUP BY uri ORDER BY q_per_req DESC;" 2>/dev/null

# 4) 스크레이프 정착 — 앱이 죽기 전에 Prometheus가 최종 카운터를 긁게 대기(>5s scrape_interval).
#    안 그러면 끝에 몰린 짧은 부하(sched·refresh)의 §① series 가 스크레이프 눈금 사이로 밀려 누락된다.
echo "[$LABEL] Prometheus 스크레이프 정착 대기(10s)"
sleep 10

echo "[$LABEL] done"
