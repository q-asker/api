#!/usr/bin/env bash
# =============================================================
# 쓰기 축(dirty tracking) before/after 측정 자동화 — 콜드 재시작 + 고정 TPS + InnoDB write 델타 + Grafana 연동
#
# 흐름: 옛 어노테이션 정리 → DB 콜드 재시작 → write-load.js 부하 → 앱/DB CPU-time·InnoDB write 델타 캡처
#       → Grafana annotation(region, tag=quiz-write-opt) → 대시보드 ${phase}_end 변수 갱신 → 요약 출력
# measure.sh(읽기 축)의 쌍둥이. 파괴적 초기화 없음(시드 보존). 같은 phase 재측정 시 옛 어노테이션만 정리.
#
# 사전: 앱을 local 프로파일로 :APP_PORT 기동(DevWriteBenchController 등록), q-asker-db 컨테이너,
#       perf-seed 시드 완료(seed.sh). **실제 Gemini 호출 없음 — 순수 DB 쓰기 경로만 구동.**
#
# 사용:  measure-write.sh <before|after>
# 조절 env: APP_PORT(8080) ACT_PORT(9090) RATE(1000) DURATION(60s)
#           WRITE_MODE(single|batch) N_SETS(41678, scale 10 기준) SET_ID_BASE(1000000)
#           MYSQL_PWD(password) GRAFANA_URL(http://localhost:3000) GRAFANA_AUTH(admin:admin)
#           DASH_UID(qasker-enh-write-ba) WRITE_DASH(대시보드 JSON 경로)
# =============================================================
set -uo pipefail

PHASE="${1:-}"
[ "$PHASE" = before ] || [ "$PHASE" = after ] || { echo "사용: measure-write.sh <before|after>" >&2; exit 64; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB=q-asker-db
DBPW="${MYSQL_PWD:-password}"
APP_PORT="${APP_PORT:-8080}"; ACT_PORT="${ACT_PORT:-9090}"
RATE="${RATE:-1000}"; DURATION="${DURATION:-60s}"
WRITE_MODE="${WRITE_MODE:-single}"
N_SETS="${N_SETS:-}"; SET_ID_BASE="${SET_ID_BASE:-}"   # 미지정 시 시드 대역을 DB에서 자동 감지
MYSQL_DB="${MYSQL_DATABASE:-test}"
GRAFANA="${GRAFANA_URL:-http://localhost:3000}"; GAUTH="${GRAFANA_AUTH:-admin:admin}"
DASH_UID="${DASH_UID:-qasker-enh-write-ba}"
WRITE_DASH="${WRITE_DASH:-$SCRIPT_DIR/dashboards/q-asker-enhancement-write-before-after.json}"
TMP="${TMPDIR:-/tmp}"

PID=$(lsof -ti "tcp:$APP_PORT" -sTCP:LISTEN 2>/dev/null | head -1)
[ -n "$PID" ] || { echo "🛑 :$APP_PORT 에서 앱을 못 찾음. local 프로파일로 앱 먼저 기동." >&2; exit 1; }

mysql_q(){ docker exec "$DB" mysql -uroot -p"$DBPW" -N -e "$1" 2>/dev/null | grep -vi warning; }
app_cputime(){ ps -p "$PID" -o cputime= | tr -d ' '; }
app_heap_b(){ curl -s -m5 "http://localhost:$ACT_PORT/actuator/prometheus" | awk '/jvm_memory_used_bytes.*area="heap"/{s+=$NF} END{printf "%.0f", s}'; }
db_cpu_usec(){ docker exec "$DB" awk '/usage_usec/{print $2}' /sys/fs/cgroup/cpu.stat 2>/dev/null; }
db_mem_b(){ docker exec "$DB" cat /sys/fs/cgroup/memory.current 2>/dev/null; }
# 쓰기 축 지표: 갱신 행 수·기록 바이트·UPDATE 문 수·핸들러 update 호출
gstat(){ mysql_q "SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_rows_updated','Innodb_data_written','Com_update','Handler_update','Innodb_os_log_written')" | awk '{print $1"="$2}'; }

echo "▶ [$PHASE] 쓰기 축 측정 시작 (app pid=$PID, $RATE TPS $DURATION, mode=$WRITE_MODE)"

echo "[1/6] 옛 '$PHASE' 어노테이션 정리 (tag=quiz-write-opt)"
for id in $(curl -s -u "$GAUTH" "$GRAFANA/api/annotations?tags=$PHASE&tags=quiz-write-opt&limit=100" 2>/dev/null | python3 -c "import sys,json;[print(a['id']) for a in json.load(sys.stdin)]" 2>/dev/null); do
  curl -s -u "$GAUTH" -X DELETE "$GRAFANA/api/annotations/$id" >/dev/null 2>&1
done

echo "[2/6] DB 콜드 재시작"
mysql_q "SET GLOBAL innodb_buffer_pool_dump_at_shutdown=OFF;" >/dev/null
docker exec "$DB" rm -f /var/lib/mysql/ib_buffer_pool 2>/dev/null || true
docker restart "$DB" >/dev/null 2>&1
for i in $(seq 1 40); do curl -s -o /dev/null -m3 "http://localhost:$APP_PORT/actuator/health" 2>/dev/null && break; sleep 1; done
# MySQL이 연결을 받을 때까지 대기 — 재시작 직후 gstat가 빈 스냅샷을 캡처하는 것 방지
for i in $(seq 1 60); do [ "$(mysql_q 'SELECT 1')" = "1" ] && break; sleep 1; done
sleep 3

# 시드 대역 자동 감지 (SET_ID_BASE/N_SETS를 env로 주지 않은 경우) — seed.sql @base 오버라이드(예: 2000000) 대응
if [ -z "$SET_ID_BASE" ] || [ -z "$N_SETS" ]; then
  read -r DBASE DCNT <<< "$(mysql_q "SELECT MIN(id), COUNT(*) FROM ${MYSQL_DB}.problem_set WHERE session_id LIKE 'seed-%'")"
  SET_ID_BASE="${SET_ID_BASE:-$DBASE}"; N_SETS="${N_SETS:-$DCNT}"
  echo "    시드 대역 자동 감지: SET_ID_BASE=$SET_ID_BASE, N_SETS=$N_SETS"
fi
{ [ -n "$SET_ID_BASE" ] && [ "$N_SETS" -gt 0 ]; } 2>/dev/null || { echo "🛑 시드 대역 감지 실패 — SET_ID_BASE·N_SETS를 env로 지정하거나 seed.sh로 시드하세요." >&2; exit 1; }

echo "[3/6] before 스냅샷"
AC0=$(app_cputime); AH0=$(app_heap_b); DC0=$(db_cpu_usec); DM0=$(db_mem_b)
gstat > "$TMP/gstat-w-pre.txt"

echo "[4/6] k6 쓰기 부하"
S=$(date +%s)
BASE_URL="http://localhost:$APP_PORT" RATE="$RATE" DURATION="$DURATION" \
  WRITE_MODE="$WRITE_MODE" N_SETS="$N_SETS" SET_ID_BASE="$SET_ID_BASE" \
  k6 run --summary-export="$TMP/k6-w-$PHASE.json" "$SCRIPT_DIR/write-load.js" > "$TMP/k6-w-$PHASE.log" 2>&1
E=$(date +%s)

echo "[5/6] after 스냅샷 + 계산"
AC1=$(app_cputime); AH1=$(app_heap_b); DC1=$(db_cpu_usec); DM1=$(db_mem_b)
gstat > "$TMP/gstat-w-post.txt"

python3 - "$PHASE" "$AC0" "$AC1" "$DC0" "$DC1" "$AH0" "$AH1" "$DM0" "$DM1" "$TMP/gstat-w-pre.txt" "$TMP/gstat-w-post.txt" "$TMP/k6-w-$PHASE.json" "$E" "$WRITE_DASH" "$TMP/measure-w-$PHASE.env" <<'PY'
import sys,json
phase,ac0,ac1,dc0,dc1,ah0,ah1,dm0,dm1,pre,post,k6f,end,dash,envf=sys.argv[1:]
def secs(t):
    t=t.strip(); d=0
    if '-' in t: d,t=t.split('-'); d=int(d)
    p=[float(x) for x in t.split(':')]
    while len(p)<3: p=[0]+p
    return d*86400+p[0]*3600+p[1]*60+p[2]
def load(f): return dict(l.strip().split('=') for l in open(f) if '=' in l)
b,a=load(pre),load(post)
k6=json.load(open(k6f))['metrics']
writes=k6['http_reqs']['count']
p95=k6.get('write_duration',{}).get('p(95)') or k6.get('http_req_duration',{}).get('p(95)',0)
appct=secs(ac1)-secs(ac0); dbct=(int(dc1)-int(dc0))/1e6
d=lambda k:int(a.get(k,0))-int(b.get(k,0))
appcpu=appct/writes*1e6 if writes else 0; dbcpu=dbct/writes*1e6 if writes else 0
rowsupd=d('Innodb_rows_updated')/writes if writes else 0
bw=d('Innodb_data_written')/writes/1024 if writes else 0      # KB/write
logw=d('Innodb_os_log_written')/writes/1024 if writes else 0  # KB/write (redo)
comupd=d('Com_update')/writes if writes else 0
print(f"\n==== [{phase}] 쓰기 축 측정 결과 ({writes:.0f} writes) ====")
print(f"  write p95             = {p95:.2f} ms")
print(f"  앱 JVM CPU-time/write = {appcpu:.1f} µs   (총 {appct:.1f}s)")
print(f"  MySQL   CPU-time/write= {dbcpu:.1f} µs   (총 {dbct:.1f}s)")
print(f"  Innodb_rows_updated/write = {rowsupd:.3f}   (dirty-tracking: 미변경이면 0에 수렴)")
print(f"  Com_update/write          = {comupd:.3f}")
print(f"  Innodb_data_written/write = {bw:.2f} KB")
print(f"  redo(log)_written/write   = {logw:.2f} KB")
print(f"  앱 힙 {int(ah0)/1048576:.0f}→{int(ah1)/1048576:.0f} MB | DB mem {int(dm0)/1048576:.0f}→{int(dm1)/1048576:.0f} MB")
print("="*54)
# annotate 텍스트용 env
open(envf,'w').write(f"P95={p95:.1f}\nACPU={appcpu:.0f}\nDCPU={dbcpu:.0f}\nROWSUPD={rowsupd:.3f}\nBW={bw:.2f}\n")
# 대시보드 ${phase}_end 변수 갱신
try:
    dd=json.load(open(dash))
    for v in dd['templating']['list']:
        if v['name']==f'{phase}_end':
            v['current']={'text':end,'value':end}
            v['query']=end
            v['options']=[{'selected':True,'text':end,'value':end}]
    json.dump(dd,open(dash,'w'),ensure_ascii=False,indent=2)
    print(f"대시보드 변수 {phase}_end = {end} 갱신됨")
except Exception as ex:
    print(f"(대시보드 변수 갱신 실패: {ex} — 수동으로 {phase}_end={end} 입력)")
PY

echo "[6/6] Grafana annotation (tag=quiz-write-opt)"
# shellcheck disable=SC1090
. "$TMP/measure-w-$PHASE.env"
START_MS=$(( S * 1000 )); END_MS=$(( E * 1000 ))
TEXT="write $RATE TPS $WRITE_MODE cold (p95 ${P95}ms, appCPU ${ACPU}µs, dbCPU ${DCPU}µs, rowsUpd/w ${ROWSUPD}, dataW/w ${BW}KB)"
curl -s -u "$GAUTH" -H "Content-Type: application/json" -X POST "$GRAFANA/api/annotations" \
  -d "{\"dashboardUID\":\"$DASH_UID\",\"time\":$START_MS,\"timeEnd\":$END_MS,\"tags\":[\"$PHASE\",\"quiz-write-opt\"],\"text\":\"$TEXT\"}" \
  | python3 -c "import sys,json;r=json.load(sys.stdin);print('annotation:',r.get('message','?'),'| id',r.get('id','none'))" 2>/dev/null || echo "(annotation 실패 — Grafana 확인)"
echo "✅ [$PHASE] 완료. 구간 $((E-S))s (epoch $S~$E). Grafana 대시보드 $DASH_UID 새로고침."
