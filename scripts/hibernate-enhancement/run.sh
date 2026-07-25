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
# 시드는 운영 실데이터(마스킹본) 복제: problem_quality_log의 실제 행(x1 원본 205행)을
#   순환 복제해 합성 세트를 채운다 — 크기·null 분포(재생성 행 ≈9%)가 합성이 아니라 실물.
#   세트당 앞 BROKEN_PER_SET행은 실데이터 해설에서 마크다운 헤더·인용을 제거해 형식 검증 실패를 유도.
#
# 리플레이 가능: 매 실행마다 합성 세트(problem_set_id > SET_BASE)를 DELETE→재시딩하므로
#   off/on·재실행이 항상 동일 초기 상태에서 시작한다(마킹 잔재 없음, 재현 가능).
#
# 흐름: 해당 모드로 bootJar 빌드(quiz-set-impl clean 포함, 캐시 혼입 방지)
#       → 인핸스 적용 여부를 클래스 멤버($$_hibernate)로 검증
#       → local,loadtest,mock 프로파일로 기동(쿼리 튜닝 스케일 DB에 연결)
#       → problem_quality_log 리플레이 시딩(합성 세트 DELETE→INSERT, 세트당 BROKEN_PER_SET개 망가진 해설)
#       → admin 토큰 발급(ROLE_ADMIN 사용자 자동 조회)
#       → explanation-review 반복 부하 → 구간 끝 epoch 출력 → 앱 종료
#
# 사용:  run.sh            — 인자 없으면 off→on 통합 실행(한 번에 A/B 완성)
#        run.sh <off|on>   — 한쪽만 실행
#   Grafana qasker-enh-rw "최신 시도" 행이 mode 라벨로 두 실행을 자동 비교한다 —
#   대시보드 시간 범위가 두 실행을 포함하기만 하면 됨(epoch은 기록·재현용).
#
# 조절 env: DB_PORT(3309=x100 기본; 3307=x1, 3308=x10), SEED_SETS(50) PER_SET(20)
#           BROKEN_PER_SET(2) 세트당 망가진 해설 10%(마킹=쓰기 대상),
#           ROUNDS(100 = 총 요청 수: 검증 1 + 부하 99), SET_BASE(9000000) 합성 세트 ID 오프셋(실 데이터 무충돌)
# 사전: 대상 MySQL 컨테이너(provision-level.sh)와 로컬 PLG 스택 기동, jq 설치
# =============================================================
set -euo pipefail

# ═══════════════════════════════════════════════════════════════════════════
# STEP 0 │ 인자 파싱 + 환경 설정 (모드, DB 레벨, 시드 파라미터)
# ═══════════════════════════════════════════════════════════════════════════
MODE="${1:-}"
if [ -z "$MODE" ]; then
  # 인자 없음 → off/on 통합 실행. env(DB_PORT·ROUNDS 등)는 자식 실행에 그대로 전파된다.
  bash "${BASH_SOURCE[0]}" off
  bash "${BASH_SOURCE[0]}" on
  echo ""
  echo "════════ off/on 통합 완료 — 대시보드(qasker-enh-rw) 최신 시도 행에서 비교 ════════"
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
SET_BASE="${SET_BASE:-9000000}"
ROUNDS="${ROUNDS:-100}"
LOG="/tmp/qasker-enh-$MODE.log"
MY() { docker exec -i -e MYSQL_PWD=password "$DB_CONTAINER" mysql --default-character-set=utf8mb4 -uroot -N qaskerdb; }

echo "════════ 인핸스먼트 $MODE — explanation-review 정조준 ($DB_CONTAINER:$DB_PORT) ════════"

# ═══════════════════════════════════════════════════════════════════════════
# STEP 1 │ 대상 MySQL 자동 기동 + 준비 대기
# ═══════════════════════════════════════════════════════════════════════════
# run.sh가 미사용 레벨을 정지해두므로(RAM 반환) 꺼져 있으면 켠다 (run.sh 의 run_level 패턴)
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
# STEP 4 │ 앱 기동 — 프로파일(local,loadtest,mock) + JFR 메서드 계측 플래그
# ═══════════════════════════════════════════════════════════════════════════
# mock: 외부 부수효과 차단
JAR="$(ls -t app/build/libs/app-*.jar | head -1)"
lsof -ti:8080 2>/dev/null | xargs kill -9 2>/dev/null || true
export JASYPT_ENCRYPTOR_PASSWORD="$(grep '^JASYPT_ENCRYPTOR_PASSWORD=' app/gradle.properties | cut -d= -f2-)"
export SPRING_PROFILES_ACTIVE=local,loadtest,mock
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$DB_PORT/qaskerdb"
# 모든 Micrometer 메트릭에 mode=off|on 태그 부여 → 대시보드 "최신 시도" 행이 모드별로 분리 표시
export MANAGEMENT_METRICS_TAGS_MODE="$MODE"
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
# APP_TASKPOLICY="-b"면 앱 JVM을 background QoS(E코어 상한)로 기동 — P/E 코어 이질성 제거 실험용.
#   taskpolicy는 정책 설정 후 exec하므로 $!(APP_PID)는 그대로 java 프로세스다.
${APP_TASKPOLICY:+taskpolicy $APP_TASKPOLICY} java "-XX:StartFlightRecording=dumponexit=true,filename=/tmp/q-asker-enh-$MODE.jfr,maxsize=200m,jdk.MethodTiming#enabled=true,jdk.MethodTiming#filter=$MT_FILTER" \
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
echo "[$MODE] quality_log 리플레이 시딩: ${SEEDED}행 (기대 $TOTAL, 강제 망가짐 $((SEED_SETS * BROKEN_PER_SET)) — 실데이터의 자연 미달분은 추가될 수 있음)"

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
FIRST=$(curl -s -X POST "http://localhost:8080/admin/problem-sets/explanation-review" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$BODY")
VIOL=$(echo "$FIRST" | jq '[.[].violationCount] | add' 2>/dev/null || echo '?')
REVIEWED=$(echo "$FIRST" | jq '[.[].reviewedCount] | add' 2>/dev/null || echo '?')
echo "[$MODE] 검증 응답: reviewed=$REVIEWED (기대 $((SEED_SETS * PER_SET))), violations=$VIOL (기대 $((SEED_SETS * BROKEN_PER_SET)) — 첫 요청이 마킹=쓰기)"

echo "[$MODE] 부하 시작 — 총 ${ROUNDS}요청 (검증 1 + 부하 $((ROUNDS - 1))) × ${SEED_SETS}세트"
for r in $(seq 2 "$ROUNDS"); do
  curl -s -o /dev/null --max-time 60 -X POST \
    "http://localhost:8080/admin/problem-sets/explanation-review" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$BODY" || true
done

sleep 12 # JFR Exporter 발행(5s 주기) → Prometheus 스크레이프(5s 간격)의 마지막 사이클까지 수집되도록 대기
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
