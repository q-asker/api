#!/usr/bin/env bash
# =============================================================
# Hibernate 인핸스먼트 E2E 측정 — explanation-review 경로 정조준 OFF/ON 비교
#
# 측정 대상: POST /admin/problem-sets/explanation-review
#   problem_quality_log를 세트 단위로 조회(읽기)해 해설 형식을 검증하고, 망가진 해설만
#   review에 마킹(쓰기)하는 경로 — 이력서의 "조회+업데이트 동시 필요" 상황 그 자체.
#   @Basic(LAZY) @LazyGroup("pass2")인 대형 질문 JSON(v1/v2)·피드백 4컬럼이
#   인핸스먼트 ON에서만 SELECT에서 제외된다 — OFF는 전량 즉시 로딩 폴백.
#
# 리플레이 가능: 매 실행마다 합성 세트(problem_set_id > SET_BASE)를 DELETE→재시딩하므로
#   off/on·재실행이 항상 동일 초기 상태에서 시작한다(마킹 잔재 없음, 재현 가능).
#
# 흐름: 해당 모드로 bootJar 빌드(quiz-set-impl clean 포함, 캐시 혼입 방지)
#       → 인핸스 적용 여부를 클래스 멤버($$_hibernate)로 검증
#       → local,mock 프로파일로 기동(쿼리 튜닝 스케일 DB에 연결)
#       → problem_quality_log 리플레이 시딩(합성 세트 DELETE→INSERT, 세트당 BROKEN_PER_SET개 망가진 해설)
#       → admin 토큰 발급(ROLE_ADMIN 사용자 자동 조회)
#       → explanation-review 반복 부하 → 구간 끝 epoch 출력 → 앱 종료
#
# 사용:  run.sh            — 인자 없으면 off→on 쌍을 PAIRS회 반복(기본 5) — 런 간 노이즈 평균화
#        run.sh <off|on>   — 한쪽 런(총 ROUNDS요청)만 1회 실행
#   Grafana qasker-enh-rw가 mode 라벨로 두 모드를 자동 비교한다 — 런마다 run 라벨이 붙어
#   "런별 최종 누적값 합산"으로 집계되므로, 시간 범위가 런들을 포함하기만 하면 됨(epoch은 기록·재현용).
#
# 조절 env: PAIRS(5 = off/on 쌍 반복 횟수 — 런 간 노이즈 평균화),
#           DB_PORT(3309=x100 기본; 3307=x1, 3308=x10), SEED_SETS(50) PER_SET(20)
#           BROKEN_PER_SET(2) 세트당 망가진 해설 10%(마킹=쓰기 대상),
#           ROUNDS(100 = 총 요청 수: 검증 1 + 부하 99), SET_BASE(1억) 합성 세트 ID 오프셋(실 데이터 최댓값 위)
# 사전: 대상 MySQL 컨테이너(provision-level.sh)와 로컬 PLG 스택 기동, jq 설치
# =============================================================
set -euo pipefail

# ═══════════════════════════════════════════════════════════════════════════
# STEP 0 │ 인자 파싱 + 환경 설정 (모드, DB 레벨, 시드 파라미터)
# ═══════════════════════════════════════════════════════════════════════════
MODE="${1:-}"
if [ -z "$MODE" ]; then
  # 인자 없음 → off/on 쌍을 PAIRS회 반복(기본 5). env(DB_PORT·ROUNDS 등)는 자식 실행에 그대로 전파된다.
  # 쌍 반복이 런 간 노이즈(그 1분의 머신 상태·JIT 운)를 평균화한다 — 요청 반복(ROUNDS)은 런 안 노이즈만 담당.
  PAIRS="${PAIRS:-5}"
  # seq 대신 산술 루프 — macOS(BSD) seq는 'seq 1 0'을 내림차순(1,0)으로 세서 PAIRS=0에 2쌍을 돌린다
  p=1
  while [ "$p" -le "$PAIRS" ]; do
    echo ""
    echo "──────── 쌍 $p/$PAIRS ────────"
    # 첫 off 런에서만 측정 테이블(enh_snapshot·mem_probe)을 DROP→CREATE 로 fresh 재생성 — 세션 멱등:
    #  재실행이 항상 깨끗한 초기 상태에서 시작(옛 "날리고 다시"를 자동화). 이후 9런은 append 돼 5쌍이 누적된다
    #  (대시보드의 런 간 평균화·모드당 500요청 유지). invocation마다 DROP하면 마지막 1런만 남아 대시보드가 깨진다.
    if [ "$p" -eq 1 ]; then ENH_RESET=1 bash "${BASH_SOURCE[0]}" off; else bash "${BASH_SOURCE[0]}" off; fi
    bash "${BASH_SOURCE[0]}" on
    p=$((p + 1))
  done
  echo ""
  echo "════════ ${PAIRS}쌍 완료 — 대시보드(qasker-enh-rw) 범위 합산으로 비교 (요청 수 기대: 모드당 $((PAIRS * ${ROUNDS:-100}))) ════════"
  exit 0
fi
[ "$MODE" = off ] || [ "$MODE" = on ] || { echo "사용: run.sh [off|on] (인자 없으면 둘 다)" >&2; exit 64; }

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DB_PORT="${DB_PORT:-3309}"
case "$DB_PORT" in
  3307) DB_CONTAINER="${DB_CONTAINER:-local-mysql-x1}" ;;
  3308) DB_CONTAINER="${DB_CONTAINER:-local-mysql-x10}" ;;
  3309) DB_CONTAINER="${DB_CONTAINER:-local-mysql-x100}" ;;
  *)    DB_CONTAINER="${DB_CONTAINER:?DB_PORT가 표준 레벨이 아니면 DB_CONTAINER 지정}" ;;
esac
SEED_SETS="${SEED_SETS:-50}"
PER_SET="${PER_SET:-20}"
BROKEN_PER_SET="${BROKEN_PER_SET:-2}"
# 실데이터 최댓값 위로 잡아야 한다 — 스케일 시딩(seed-scale.sh)이 x100에서 problem_set_id를
# 99,016,088까지 밀어올리므로 1억으로 둔다. 이 값이 실데이터 범위 안에 들어가면 두 가지가 동시에 깨진다:
#   ① DELETE ... WHERE problem_set_id > SET_BASE 가 실데이터를 지운다
#   ② 원천 풀(problem_set_id <= SET_BASE)이 극히 일부로 쪼그라들어 순환 복제 횟수가 폭증한다
SET_BASE="${SET_BASE:-100000000}"
ROUNDS="${ROUNDS:-100}"
# 측정 대상 경로 — 부하(curl)와 집계(SQL 주석 필터) 양쪽이 같은 값을 봐야 한다.
# 한쪽만 바꾸면 부하는 도는데 집계가 0건이 되어 조용히 빈 결과가 나온다.
# 같은 값이 RequestResourceMetricsFilter.MEASURED_URI와 대시보드 패널 타깃에도 있다(계층이 달라 공유 불가).
ENDPOINT="/admin/problem-sets/explanation-review"
LOG="/tmp/qasker-enh-$MODE.log"
MY() { docker exec -i -e MYSQL_PWD=password "$DB_CONTAINER" mysql --default-character-set=utf8mb4 -uroot -N qaskerdb; }
# 표 출력용(헤더 유지) — STEP 8.5의 문장 단위 귀속 리포트 전용
MYT() { docker exec -i -e MYSQL_PWD=password "$DB_CONTAINER" mysql --default-character-set=utf8mb4 -uroot --table qaskerdb; }

echo "════════ 인핸스먼트 $MODE — explanation-review 정조준 ($DB_CONTAINER:$DB_PORT) ════════"

# ═══════════════════════════════════════════════════════════════════════════
# STEP 1 │ 대상 MySQL 자동 기동 + 준비 대기
# ═══════════════════════════════════════════════════════════════════════════
# query-tuning의 run-all.sh가 미사용 레벨을 정지해두므로(RAM 반환) 꺼져 있으면 켠다
if ! docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER"; then
  docker start "$DB_CONTAINER" > /dev/null 2>&1 \
    || { echo "🛑 $DB_CONTAINER 컨테이너가 없음 — query-tuning README의 provision·시딩 먼저" >&2; exit 1; }
  echo "[$MODE] $DB_CONTAINER 자동 기동, 준비 대기..."
fi
for i in $(seq 1 30); do
  docker exec -e MYSQL_PWD=password "$DB_CONTAINER" mysqladmin -uroot ping 2>/dev/null | grep -q alive && break
  [ "$i" = 30 ] && { echo "🛑 $DB_CONTAINER 준비 시간 초과" >&2; exit 1; }
  sleep 2
done

# ═══════════════════════════════════════════════════════════════════════════
# STEP 2 │ 모드별 bootJar 빌드 (off는 -PdisableHibernateEnhancement)
# ═══════════════════════════════════════════════════════════════════════════
# ON/OFF 어느 쪽이든 quiz-set-impl을 clean해 이전 모드의 계측 클래스가 섞이지 않게 한다
if [ "$MODE" = on ]; then
  ./gradlew :quiz-set-impl:clean :app:bootJar -q
else
  ./gradlew :quiz-set-impl:clean :app:bootJar -PdisableHibernateEnhancement -q
fi

# ═══════════════════════════════════════════════════════════════════════════
# STEP 3 │ 인핸스먼트 적용 여부 게이트 (javap로 $$_hibernate 멤버 검사)
# ═══════════════════════════════════════════════════════════════════════════
# 잘못된 빌드로 측정하는 사고 차단
CNT=$(javap -p -cp modules/quiz-set/impl/build/classes/java/main \
  com.icc.qasker.quizset.entity.ProblemQualityLog | grep -c '\$\$_hibernate' || true)
if [ "$MODE" = on ] && [ "$CNT" -eq 0 ]; then echo "🛑 on인데 인핸스 미적용 — 빌드 확인" >&2; exit 1; fi
if [ "$MODE" = off ] && [ "$CNT" -gt 0 ]; then echo "🛑 off인데 인핸스 잔재 — clean 실패" >&2; exit 1; fi
echo "[$MODE] 인핸스먼트 상태 확인 (\$\$_hibernate 멤버 ${CNT}개)"

# ═══════════════════════════════════════════════════════════════════════════
# STEP 4 │ 앱 기동 — 프로파일(local,mock) + JFR 메서드 계측 플래그
# ═══════════════════════════════════════════════════════════════════════════
# mock: 외부 부수효과 차단
JAR="$(ls -t app/build/libs/app-*.jar | head -1)"
lsof -ti:8080 2>/dev/null | xargs kill -9 2>/dev/null || true
export JASYPT_ENCRYPTOR_PASSWORD="$(grep '^JASYPT_ENCRYPTOR_PASSWORD=' app/gradle.properties | cut -d= -f2-)"
export SPRING_PROFILES_ACTIVE=local,mock  # mock: 외부 호출·실쓰기 mock + 레이트리밋 off·분석 DB(구 loadtest 흡수)
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$DB_PORT/qaskerdb"
# 모든 Micrometer 메트릭에 mode=off|on 태그 부여 → 대시보드가 모드별로 분리 표시
export MANAGEMENT_METRICS_TAGS_MODE="$MODE"
# 런 고유 태그 — 런마다 별도 시리즈가 되어, 대시보드가 increase() 추정 대신
# "시리즈별 마지막 누적값의 합"(last_over_time 합산)으로 외삽 없는 정확한 pooled를 계산한다.
RUN_TAG="$(date +%s)"   # STEP 8.5의 enh_snapshot 적재에도 같은 값을 쓴다(앱 지표와 런 단위 대조 가능)
export MANAGEMENT_METRICS_TAGS_RUN="$RUN_TAG"
# 측정 중에는 가상 스레드 비활성 — ThreadMXBean 요청 스레드 CPU/할당 계측(RequestResourceMetricsFilter)이
# 가상 스레드에서 -1을 반환하기 때문(JDK-8303251). off/on 동일 조건이므로 A/B 공정성은 유지된다.
export SPRING_THREADS_VIRTUAL_ENABLED=false
: > "$LOG"
# JEP 520(jdk.MethodTiming, JDK 25) 확정 필터: 순수 CPU 5종 + 혼합 창 1종.
#   더티체크 4종(순수 CPU): ①ON 장부 읽기 ②이름→인덱스 변환 ③setter 시점 값 비교
#                 ④OFF 스냅샷 비교(대조군, ON에서 0건이어야 정상)
#   읽기 경로 1종(순수 CPU): ⑤readRow = 조립 창(수신 버퍼에서 값 추출·디코딩·엔티티 조립, 행당 1회)
#   혼합 창 1종(대기 포함 — CPU 주장 금지, 지연 분해용): ⑥executeQuery = 쿼리 창
#                 (서버 실행+전송+TLS 복호화+패킷 파싱, 쿼리당 1회. wall이므로 "로딩 구간"으로만 읽는다)
#   단일 도구 원칙: hibernate-jfr(DirtyCalculationEvent)는 의존성째 제거됨 — 측정 대상 메서드 안에서
#   이벤트 발행이 일어나 OFF 수치만 부풀리는 관측자 비용이었기 때문.
#   주의: ⑥ 외의 대기 혼입 메서드(readFully·소켓 read류)는 시간 목적으로 넣지 말 것 —
#   창 밖(커밋·풀 ping) 혼입 탓에 부분합이 전체를 넘는 무효 계측이 됨을 실측으로 확인.
#   고빈도 컬럼 단위 메서드(getString 등)도 금지 — readRow 창을 호출당 ~200ns씩 오염(실측 11.6→6.57µs).
#   카운트 실험은 MT_EXTRA_FILTER로 별도 런.
MT_FILTER="org.hibernate.event.internal.DefaultFlushEntityEventListener::getDirtyPropertiesFromSelfDirtinessTracker;org.hibernate.persister.entity.AbstractEntityPersister::resolveDirtyAttributeIndexes;org.hibernate.bytecode.enhance.internal.bytebuddy.InlineDirtyCheckerEqualsHelper::areEquals;org.hibernate.event.internal.DefaultFlushEntityEventListener::performDirtyCheck;org.hibernate.sql.results.internal.StandardRowReader::readRow;org.hibernate.sql.results.jdbc.internal.DeferredResultSetAccess::executeQuery${MT_EXTRA_FILTER:+;$MT_EXTRA_FILTER}"
java "-XX:StartFlightRecording=dumponexit=true,filename=/tmp/q-asker-enh-$MODE.jfr,maxsize=200m,jdk.MethodTiming#enabled=true,jdk.MethodTiming#filter=$MT_FILTER" \
  -jar "$JAR" >> "$LOG" 2>&1 &

APP_PID=$!
trap 'kill $APP_PID 2>/dev/null || true; wait $APP_PID 2>/dev/null || true' EXIT

# ═══════════════════════════════════════════════════════════════════════════
# STEP 5 │ 부팅 대기 (health 응답 폴링, 최대 90초)
# ═══════════════════════════════════════════════════════════════════════════
for i in $(seq 1 45); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://localhost:8080/v3/api-docs || echo 000)
  [ "$code" = "200" ] && { echo "[$MODE] app up (~$((i * 2))s)"; break; }
  kill -0 $APP_PID 2>/dev/null || {
    echo "[$MODE] 부팅 실패" >&2
    grep -iE "APPLICATION FAILED|Caused by" "$LOG" | head -3 >&2
    exit 1
  }
  sleep 2
done

# ═══════════════════════════════════════════════════════════════════════════
# STEP 6 │ problem_quality_log 리플레이 시딩 (실데이터 순환 복제 → 합성 세트)
# ═══════════════════════════════════════════════════════════════════════════
# 원천: 실제 운영 행(마스킹본, problem_set_id <= SET_BASE)을 결정적 순서(ROW_NUMBER)로
#   순환 복제해 SEED_SETS×PER_SET 그리드를 채운다 — 크기·null 분포(재생성 행 ≈9%)가 실물.
# 망가진 해설: 세트당 앞 BROKEN_PER_SET행은 실데이터 해설에서 마크다운 헤더(#)·인용(>)을
#   제거해 형식 검증 실패를 유도(길이는 실물 유지) → 마킹=쓰기 경로 재현.
# 리플레이: 매 런 DELETE→INSERT라 off/on·재실행이 항상 동일 초기 상태(review=NULL).
TOTAL=$((SEED_SETS * PER_SET))
AVAIL=$(echo "SELECT COUNT(*) FROM problem_quality_log WHERE problem_set_id <= $SET_BASE AND v1_question_json IS NOT NULL AND v1_explanation IS NOT NULL;" | MY)
[ "$AVAIL" -gt 0 ] || { echo "🛑 복제할 실데이터가 없음 (problem_quality_log 비었음)" >&2; exit 1; }
REPS=$(((TOTAL + AVAIL - 1) / AVAIL))
echo "[$MODE] 실데이터 원천 ${AVAIL}행 × 순환 ${REPS}회 → ${TOTAL}행 시딩"
MY <<SQL
SET SESSION cte_max_recursion_depth = 10000;
DELETE FROM problem_quality_log WHERE problem_set_id > $SET_BASE;
INSERT INTO problem_quality_log
  (problem_set_id, number, v1_question_json, v1_explanation, v1_feedback, v2_question_json, v2_explanation, created_at)
WITH RECURSIVE reps AS (SELECT 0 AS k UNION ALL SELECT k + 1 FROM reps WHERE k + 1 < $REPS),
src AS (
  SELECT v1_question_json, v1_explanation, v1_feedback, v2_question_json, v2_explanation,
         ROW_NUMBER() OVER (ORDER BY problem_set_id, number) AS rn0
  FROM problem_quality_log
  WHERE problem_set_id <= $SET_BASE
    AND v1_question_json IS NOT NULL AND v1_explanation IS NOT NULL
),
grid AS (
  SELECT src.*, (reps.k * $AVAIL + src.rn0) AS rn
  FROM src CROSS JOIN reps
)
SELECT
  $SET_BASE + CEILING(rn / $PER_SET),
  ((rn - 1) % $PER_SET) + 1,
  v1_question_json,
  CASE WHEN ((rn - 1) % $PER_SET) + 1 <= $BROKEN_PER_SET
       THEN REPLACE(REPLACE(v1_explanation, '#', ''), '>', '')
       ELSE v1_explanation END,
  v1_feedback, v2_question_json, v2_explanation,
  NOW(6)
FROM grid
WHERE rn <= $TOTAL;
SQL
SEEDED=$(echo "SELECT COUNT(*) FROM problem_quality_log WHERE problem_set_id > $SET_BASE;" | MY)
echo "[$MODE] quality_log 리플레이 시딩: ${SEEDED}행 (기대 $TOTAL, 강제 망가짐 $((SEED_SETS * BROKEN_PER_SET)) — 구식 템플릿 해설은 마커 제거 후에도 통과할 수 있어 실제 위반은 그 이하)"

# ═══════════════════════════════════════════════════════════════════════════
# STEP 7 │ admin 토큰 발급 (ROLE_ADMIN 사용자 자동 조회 → 로컬 토큰 헬퍼)
# ═══════════════════════════════════════════════════════════════════════════
ADMIN_ID=$(echo "SELECT user_id FROM user WHERE role='ROLE_ADMIN' LIMIT 1;" | MY)
[ -n "$ADMIN_ID" ] || { echo "🛑 ROLE_ADMIN 사용자가 DB에 없음" >&2; exit 1; }
TOKEN=$(curl -s "http://localhost:8080/local/token?userId=$ADMIN_ID")
case "$TOKEN" in unknown*|"") echo "🛑 admin 토큰 발급 실패: $TOKEN" >&2; exit 1 ;; esac
echo "[$MODE] admin 토큰 ok (user=$ADMIN_ID)"

# ═══════════════════════════════════════════════════════════════════════════
# STEP 8 │ 부하 — 검증 1회 + ROUNDS회 반복 (요청당 SEED_SETS세트 × PER_SET행)
# ═══════════════════════════════════════════════════════════════════════════
# 첫 요청이 망가진 해설을 마킹(쓰기)하고, 이후 요청은 같은 값 재마킹(장부 깨끗)
BODY=$(jq -cn --argjson base "$SET_BASE" --argjson n "$SEED_SETS" \
  '{setIds: [range($base + 1; $base + $n + 1)]}')
echo "[$MODE] 요청 DTO (매 요청 동일):"
echo "$BODY" | jq -c .

# 부하 직전 MySQL 관측 기준선 초기화 — 시딩·토큰 조회 같은 준비 SQL을 집계에서 배제한다.
# 이 시점 이후 이 DB에 오는 트래픽은 사실상 부하 요청과 커넥션 풀 유지뿐이므로,
# 아래 Bytes_sent 차분은 rate() 창 없이 부하 구간을 정확히 감싼다.
MY <<'SQL'
-- ── perf_schema 활성화 (한곳 집약) — 컨테이너 재기동마다 초기화되므로 매 런 재설정. 전부 기본 YES/OFF 무관하게 멱등 ──
--  소비자(수집처)
UPDATE performance_schema.setup_consumers SET ENABLED='YES'
  WHERE NAME IN ('events_statements_history_long',  -- 문장 링버퍼: enh_snapshot 소스
                 'events_statements_cpu');           -- 문장별 CPU_TIME(cpu_ms)
--  계측기(생산자)
UPDATE performance_schema.setup_instruments SET ENABLED='YES' WHERE NAME LIKE 'memory/%';  -- MAX_TOTAL_MEMORY·mem_probe HIGH
-- ── 창 리셋 (부하 직전 — 이 시점 이후 트래픽만 집계) ──
TRUNCATE performance_schema.events_statements_history_long;
TRUNCATE performance_schema.memory_summary_global_by_event_name;
SQL
BYTES_SENT0=$(echo "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Bytes_sent';" | MY)

FIRST=$(curl -s -X POST "http://localhost:8080$ENDPOINT" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$BODY")
VIOL=$(echo "$FIRST" | jq '[.[].violationCount] | add' 2>/dev/null || echo '?')
REVIEWED=$(echo "$FIRST" | jq '[.[].reviewedCount] | add' 2>/dev/null || echo '?')
echo "[$MODE] 검증 응답: reviewed=$REVIEWED (기대 $((SEED_SETS * PER_SET))), violations=$VIOL (기대 ≤$((SEED_SETS * BROKEN_PER_SET)) — 첫 요청이 마킹=쓰기, off/on이 같은 값이어야 공정)"

echo "[$MODE] 부하 시작 — 총 ${ROUNDS}요청 (검증 1 + 부하 $((ROUNDS - 1))) × ${SEED_SETS}세트"
for r in $(seq 2 "$ROUNDS"); do
  curl -s -o /dev/null --max-time 60 -X POST \
    "http://localhost:8080$ENDPOINT" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$BODY" || true
done

# ═══════════════════════════════════════════════════════════════════════════
# STEP 8.5 │ MySQL 문장 단위 귀속 — SQL 주석(reqId) × performance_schema
# ═══════════════════════════════════════════════════════════════════════════
# 대시보드의 MySQL 패널은 "off/on 런이 시간상 겹치지 않는다"에 기대어 rate() 표본을
# 시각으로 걸러내는 구간 귀속 방식이라, 런 길이 비대칭·rate 창 번짐만큼 오차가 남는다.
# 여기서는 CountingInspector가 이미 모든 SQL 앞에 붙여둔 /* reqId=... uri=... */ 주석을
# 열쇠로 삼아 서버가 기록한 문장 하나하나를 요청에 직접 귀속시킨다 — 시간 추정이 없다.
# 단위: TIMER_WAIT·CPU_TIME 모두 피코초(/1e9 = ms).
# 한계: events_statements_history_long은 10,000행 링버퍼라 오래된 요청부터 밀려난다.
#   집계요청수가 ROUNDS보다 작으면 그만큼 밀려난 것이니(최근 요청만 남음) 표본 수로 판단할 것.
BYTES_SENT1=$(echo "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Bytes_sent';" | MY)
echo ""
# 런 경계 오염 감시 — 집계 창(TRUNCATE~지금)에 다른 모드의 문장이 있으면 안 된다.
# Bytes_sent·형태별 집계는 주석이 없어 이 검사를 못 하지만, 같은 창을 공유하므로 이 값이 0이면 그쪽도 깨끗하다.
XMODE=$(echo "SELECT COUNT(*) FROM performance_schema.events_statements_history_long
  WHERE SQL_TEXT LIKE '/* reqId=%$ENDPOINT%' AND SQL_TEXT NOT LIKE '%mode=$MODE%';" | MY)
[ "$XMODE" = 0 ] || echo "⚠️  [$MODE] 집계 창에 다른 모드 문장 ${XMODE}건 혼입 — 런 경계 오염, 수치 신뢰 불가"
# 링버퍼를 영구 테이블로 굳힌다 — query-tuning의 trace_snapshot과 같은 패턴.
# 링버퍼는 휘발성이라 다음 런이 지우고, 실행 1건마다 1행이라 Prometheus 지표로도 못 내보낸다.
# 굳혀두면 Grafana가 MySQL 데이터소스로 직접 조회할 수 있고, 주석의 mode가 컬럼이 되므로
# 시간 게이팅 없이 WHERE mode='off'로 정확히 갈린다(구간 귀속의 경계 번짐이 사라짐).
MY <<SQL
${ENH_RESET:+DROP TABLE IF EXISTS enh_snapshot;}
CREATE TABLE IF NOT EXISTS enh_snapshot (
  ts DATETIME(3) NOT NULL, run BIGINT NOT NULL, mode VARCHAR(3) NOT NULL,
  reqId VARCHAR(16) NOT NULL, sql_stripped VARCHAR(150),
  ms DECIMAL(14,3), cpu_ms DECIMAL(14,3), mem_bytes BIGINT,
  sent BIGINT, examined BIGINT, no_index TINYINT, event_id BIGINT,
  INDEX(ts), INDEX(run), INDEX(mode)
);
INSERT INTO enh_snapshot (ts,run,mode,reqId,sql_stripped,ms,cpu_ms,mem_bytes,sent,examined,no_index,event_id)
SELECT NOW(3), $RUN_TAG, '$MODE',
  SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'reqId=',-1),' ',1),
  LEFT(TRIM(SUBSTRING_INDEX(SQL_TEXT,'*/',-1)),150),
  TIMER_WAIT/1e9, CPU_TIME/1e9, MAX_TOTAL_MEMORY,
  ROWS_SENT, ROWS_EXAMINED, NO_INDEX_USED, EVENT_ID
FROM performance_schema.events_statements_history_long
WHERE SQL_TEXT LIKE '/* reqId=%$ENDPOINT%' AND SQL_TEXT LIKE '%mode=$MODE%';
SQL
echo "[$MODE] enh_snapshot 적재: $(echo "SELECT COUNT(*) FROM enh_snapshot WHERE run=$RUN_TAG;" | MY)문장 (run=$RUN_TAG)"
# 하드 가드 — history_long(1만행 링버퍼) 단일 집계라 밀리면 유실분을 복원할 길이 없다.
# 밀림이 나면 요청이 통째로 유실돼 집계가 조용히 과소가 되므로, 캡처 요청수 != ROUNDS면 신뢰 불가로 보고 중단한다.
# (현재 파라미터에선 런당 ~183문장이라 여유가 크지만, ROUNDS·SEED_SETS 상향 시 이 가드가 유실을 잡는다.)
CAPTURED=$(echo "SELECT COUNT(DISTINCT reqId) FROM enh_snapshot WHERE run=$RUN_TAG;" | MY)
if [ "$CAPTURED" != "$ROUNDS" ]; then
  echo "🛑 [$MODE] 캡처 ${CAPTURED}/${ROUNDS} 요청 — history_long 링버퍼 밀림(문장 유실)." >&2
  echo "   history_long 단일 집계라 유실분을 복원 못 한다. 수치 신뢰 불가 — 중단." >&2
  echo "   대처: ROUNDS·SEED_SETS·PER_SET를 낮추거나 provision-level.sh의 history_long_size를 키울 것." >&2
  exit 1
fi
# 계측기별 메모리 창 스냅샷(mem_probe) — 인핸스먼트 전용. 단일 엔드포인트 부하라 창이 사실상 그 엔드포인트다
#  (배경 폴링·풀 검증은 mode-invariant라 off/on 차분에서 상쇄). String::value·temptable·Filesort HIGH =
#  대형 질문 JSON 지연로딩이 줄이는 메모리의 계측기별 내역. 3계측기는 문장 단위로 안 쪼개져 런/모드당 1행(누적).
#  total_bytes = 이 모드 요청 문장의 총 메모리 피크(= memory/% 합, query-tuning의 trace_snapshot.mem_bytes와 같은 공통 지표).
# 각 값을 세션 변수로 따로 계산한 뒤 INSERT ... VALUES 로 넣는다. events_statements_history_long(MESSAGE_TEXT
#  varchar128 컬럼 보유)에 대한 스칼라 서브쿼리를 INSERT ... SELECT 안에서 다른 서브쿼리와 섞으면
#  performance_schema 버그로 ERROR 1406(Data too long for 'MESSAGE_TEXT')가 난다 — 분리하면 회피된다.
MY <<SQL
${ENH_RESET:+DROP TABLE IF EXISTS mem_probe;}
CREATE TABLE IF NOT EXISTS mem_probe (
  ts DATETIME(3) NOT NULL, run BIGINT NOT NULL, mode VARCHAR(3) NOT NULL,
  string_value_bytes BIGINT, temptable_bytes BIGINT, filesort_bytes BIGINT, total_bytes BIGINT,
  INDEX(ts), INDEX(run), INDEX(mode)
);
SET @sv  := (SELECT HIGH_NUMBER_OF_BYTES_USED FROM performance_schema.memory_summary_global_by_event_name WHERE EVENT_NAME='memory/sql/String::value');
SET @tt  := (SELECT COALESCE(SUM(HIGH_NUMBER_OF_BYTES_USED),0) FROM performance_schema.memory_summary_global_by_event_name WHERE EVENT_NAME IN ('memory/temptable/physical_ram','memory/temptable/physical_disk'));
SET @fs  := (SELECT HIGH_NUMBER_OF_BYTES_USED FROM performance_schema.memory_summary_global_by_event_name WHERE EVENT_NAME='memory/sql/Filesort_buffer::sort_keys');
SET @tot := (SELECT MAX(MAX_TOTAL_MEMORY) FROM performance_schema.events_statements_history_long WHERE SQL_TEXT LIKE '/* reqId=%$ENDPOINT%' AND SQL_TEXT LIKE '%mode=$MODE%');
INSERT INTO mem_probe (ts,run,mode,string_value_bytes,temptable_bytes,filesort_bytes,total_bytes)
VALUES (NOW(3), $RUN_TAG, '$MODE', @sv, @tt, @fs, @tot);
SQL
echo "[$MODE] mem_probe 적재: String::value·temptable·Filesort HIGH + 총피크 (run=$RUN_TAG)"
echo "[$MODE] MySQL 문장 단위 귀속 — 요청당 평균 (주석 reqId·mode 기준, 구간 추정 없음)"
MYT <<SQL
SELECT COUNT(*) AS 집계요청수, ROUND(AVG(stmts),1) AS 문장수,
       ROUND(AVG(ms),2) AS 실행시간ms, ROUND(AVG(cpu_ms),2) AS 서버CPU_ms,
       ROUND(AVG(sent),0) AS 반환행수, ROUND(AVG(examined),0) AS 스캔행수
FROM (
  SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'reqId=',-1),' ',1) AS req,
         COUNT(*) AS stmts, SUM(TIMER_WAIT)/1e9 AS ms, SUM(CPU_TIME)/1e9 AS cpu_ms,
         SUM(ROWS_SENT) AS sent, SUM(ROWS_EXAMINED) AS examined
  FROM performance_schema.events_statements_history_long
  WHERE SQL_TEXT LIKE '/* reqId=%$ENDPOINT%' AND SQL_TEXT LIKE '%mode=$MODE%'
  GROUP BY req
) t;
SQL
echo "[$MODE] 요청당 DB 송신 바이트: $(( (BYTES_SENT1 - BYTES_SENT0) / ROUNDS )) B (부하 구간 Bytes_sent 차분 / $ROUNDS)"
echo "[$MODE] 실제 발행된 SELECT의 컬럼 목록 (off/on 차이의 직접 증거):"
echo "SELECT SUBSTRING(SQL_TEXT, LOCATE('*/', SQL_TEXT) + 2, 400) FROM performance_schema.events_statements_history_long
      WHERE SQL_TEXT LIKE '%from problem_quality_log%' ORDER BY TIMER_WAIT DESC LIMIT 1;" | MY

sleep 8 # JFR Exporter 발행(5s 주기) → Prometheus 스크레이프(1s 간격)의 마지막 사이클까지 수집되도록 대기
END=$(date +%s)

# ═══════════════════════════════════════════════════════════════════════════
# STEP 9 │ 앱 종료 → JFR 덤프 회수 → 메서드 타이밍 표 출력
# ═══════════════════════════════════════════════════════════════════════════
# 자기검증 기대값: OFF는 performDirtyCheck=요청수×행수·tracker 3종 0건, ON은 performDirtyCheck 0건.
# 대시보드용 수집은 JfrMethodTimingExporter가 담당 — 이 표는 런 직후 터미널 확인용(.jfr 원본 기준).
kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true
trap - EXIT # 이미 종료했으므로 trap 해제
JFRF="/tmp/q-asker-enh-$MODE.jfr"
if [ -f "$JFRF" ] && command -v jfr >/dev/null; then
  echo "[$MODE] 순수 CPU 5종 메서드 타이밍 (jdk.MethodTiming, $JFRF):"
  jfr view method-timing "$JFRF" 2>/dev/null || echo "  (method-timing 뷰 없음 — JDK 25 jfr인지 확인)"
fi
echo "════════════════════════════════════════"
echo "[$MODE] 완료 — 구간 끝 epoch(기록용): $END"
echo "대시보드(qasker-enh-rw)는 시간 범위에 이 실행이 포함되면 자동 표시된다."
