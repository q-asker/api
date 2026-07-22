#!/usr/bin/env bash
# =============================================================
# Hibernate 인핸스먼트 E2E 측정 — explanation-review 경로 정조준 OFF/ON 비교
#
# 측정 대상: POST /admin/problem-sets/explanation-review
#   problem_quality_log에서 해설(즉시 로딩)만 읽어 형식 검증하는 경로.
#   @Basic(LAZY) @LazyGroup("pass2")인 대형 질문 JSON(v1/v2)·피드백 4컬럼이
#   인핸스먼트 ON에서만 SELECT에서 제외된다 — OFF는 전량 즉시 로딩 폴백.
#
# 흐름: 해당 모드로 bootJar 빌드(quiz-set-impl clean 포함, 캐시 혼입 방지)
#       → 인핸스 적용 여부를 클래스 멤버($$_hibernate)로 검증
#       → local,loadtest,mock 프로파일로 기동(쿼리 튜닝 스케일 DB에 연결)
#       → problem_quality_log 멱등 시딩(비어 있는 세트에만 INSERT IGNORE)
#       → admin 토큰 발급(ROLE_ADMIN 사용자 자동 조회)
#       → explanation-review 반복 부하 → 구간 끝 epoch 출력 → 앱 종료
#
# 사용:  run.sh <off|on>
#   off 실행 → 출력된 epoch을 대시보드 before_end에
#   on  실행 → 출력된 epoch을 after_end에
#   (Grafana qasker-enh-rw, instance=springboot-local — 앱 액추에이터를 기존
#    spring-boot 스크레이프 잡이 수집하므로 별도 설정 불필요)
#
# 조절 env: DB_PORT(3307=x1; 3308=x10, 3309=x100), SEED_SETS(50) PER_SET(16)
#           ROUNDS(100) — 요청당 SEED_SETS개 세트 × PER_SET행을 읽는다
# 사전: 대상 MySQL 컨테이너(provision-level.sh)와 로컬 PLG 스택 기동, jq 설치
# =============================================================
set -euo pipefail

MODE="${1:-}"
[ "$MODE" = off ] || [ "$MODE" = on ] || { echo "사용: run.sh <off|on>" >&2; exit 64; }

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
DB_PORT="${DB_PORT:-3307}"
case "$DB_PORT" in
  3307) DB_CONTAINER="${DB_CONTAINER:-local-mysql-x1}" ;;
  3308) DB_CONTAINER="${DB_CONTAINER:-local-mysql-x10}" ;;
  3309) DB_CONTAINER="${DB_CONTAINER:-local-mysql-x100}" ;;
  *)    DB_CONTAINER="${DB_CONTAINER:?DB_PORT가 표준 레벨이 아니면 DB_CONTAINER 지정}" ;;
esac
SEED_SETS="${SEED_SETS:-50}"
PER_SET="${PER_SET:-16}"
ROUNDS="${ROUNDS:-100}"
LOG="/tmp/qasker-enh-$MODE.log"
MY() { docker exec -i -e MYSQL_PWD=password "$DB_CONTAINER" mysql --default-character-set=utf8mb4 -uroot -N qaskerdb; }

echo "════════ 인핸스먼트 $MODE — explanation-review 정조준 ($DB_CONTAINER:$DB_PORT) ════════"

# 1) 빌드 — ON/OFF 어느 쪽이든 quiz-set-impl을 clean해 이전 모드의 계측 클래스가 섞이지 않게 한다
if [ "$MODE" = on ]; then
  ./gradlew :quiz-set-impl:clean :app:bootJar -q
else
  ./gradlew :quiz-set-impl:clean :app:bootJar -PdisableHibernateEnhancement -q
fi

# 2) 인핸스 적용 여부 검증 — 잘못된 빌드로 측정하는 사고 차단
CNT=$(javap -p -cp modules/quiz-set/impl/build/classes/java/main \
  com.icc.qasker.quizset.entity.ProblemQualityLog | grep -c '\$\$_hibernate' || true)
if [ "$MODE" = on ] && [ "$CNT" -eq 0 ]; then echo "🛑 on인데 인핸스 미적용 — 빌드 확인" >&2; exit 1; fi
if [ "$MODE" = off ] && [ "$CNT" -gt 0 ]; then echo "🛑 off인데 인핸스 잔재 — clean 실패" >&2; exit 1; fi
echo "[$MODE] 인핸스먼트 상태 확인 (\$\$_hibernate 멤버 ${CNT}개)"

# 3) 앱 기동 (mock: 외부 부수효과 차단)
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
java -jar "$JAR" >> "$LOG" 2>&1 &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null || true; wait $APP_PID 2>/dev/null || true' EXIT

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

# 4) problem_quality_log 멱등 시딩 — 세트 1..SEED_SETS × 문항 1..PER_SET
#    v1_question_json: 대형 필러(≈4KB, lazy 대상 — ON에서 SELECT 제외되는 컬럼)
#    v1_explanation:  형식 검증을 통과하는 정형 해설 → 마킹(쓰기) 없이 읽기 경로 격리
MY <<SQL
SET SESSION cte_max_recursion_depth = 100000;
INSERT IGNORE INTO problem_quality_log
  (problem_set_id, number, v1_question_json, v1_explanation, created_at)
WITH RECURSIVE sets AS (SELECT 1 s UNION ALL SELECT s+1 FROM sets WHERE s < $SEED_SETS),
               nums AS (SELECT 1 n UNION ALL SELECT n+1 FROM nums WHERE n < $PER_SET)
SELECT s, n,
  CONCAT('{"stem":"', REPEAT('가나다라마바사아자차 ', 360), '","selections":[]}'),
  CONCAT('- **평가 수준**: 적용\n\n## 정답 선택지\n\n> 정답 선지\n\n',
         REPEAT('정답 근거 해설 본문입니다. ', 40),
         '\n\n## 오답 선택지\n\n> 오답 선지\n\n오답 근거 해설 본문입니다.\n'),
  NOW(6)
FROM sets, nums;
SQL
SEEDED=$(echo "SELECT COUNT(*) FROM problem_quality_log WHERE problem_set_id <= $SEED_SETS;" | MY)
echo "[$MODE] quality_log 시딩 확인: ${SEEDED}행 (기대 $((SEED_SETS * PER_SET)))"

# 5) admin 토큰 — ROLE_ADMIN 사용자를 DB에서 찾아 loadtest 토큰 헬퍼로 발급
ADMIN_ID=$(echo "SELECT user_id FROM user WHERE role='ROLE_ADMIN' LIMIT 1;" | MY)
[ -n "$ADMIN_ID" ] || { echo "🛑 ROLE_ADMIN 사용자가 DB에 없음" >&2; exit 1; }
TOKEN=$(curl -s "http://localhost:8080/local/token?userId=$ADMIN_ID")
case "$TOKEN" in unknown*|"") echo "🛑 admin 토큰 발급 실패: $TOKEN" >&2; exit 1 ;; esac
echo "[$MODE] admin 토큰 ok (user=$ADMIN_ID)"

# 6) explanation-review 반복 부하 — 요청당 SEED_SETS세트 × PER_SET행 읽기+검증
BODY=$(jq -cn --argjson n "$SEED_SETS" '{setIds: [range(1; $n + 1)]}')
FIRST=$(curl -s -X POST "http://localhost:8080/admin/problem-sets/explanation-review" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$BODY")
VIOL=$(echo "$FIRST" | jq '[.[].violationCount] | add' 2>/dev/null || echo '?')
REVIEWED=$(echo "$FIRST" | jq '[.[].reviewedCount] | add' 2>/dev/null || echo '?')
echo "[$MODE] 검증 응답 확인: reviewed=$REVIEWED, violations=$VIOL (0이어야 읽기 격리)"

echo "[$MODE] 부하 시작 — ${ROUNDS}회 × ${SEED_SETS}세트"
for r in $(seq 1 "$ROUNDS"); do
  curl -s -o /dev/null --max-time 60 -X POST \
    "http://localhost:8080/admin/problem-sets/explanation-review" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$BODY" || true
done

sleep 6 # 마지막 스크레이프(5s 간격)까지 수집되도록 잠시 대기
END=$(date +%s)
echo "════════════════════════════════════════"
if [ "$MODE" = off ]; then
  echo "[off] 완료 — 대시보드 before_end에 입력: $END"
else
  echo "[on]  완료 — 대시보드 after_end에 입력: $END"
fi
