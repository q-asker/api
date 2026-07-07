#!/usr/bin/env bash
# =============================================================
# 읽기 축 before/after 측정 자동화 (Q3/Q10) — 콜드 재시작 + 고정 TPS + 격리-robust 지표 + Grafana 연동
#
# 흐름: 옛 어노테이션 정리 → DB 콜드 재시작 → k6 부하 → 앱/DB CPU-time·bytes/read·InnoDB 델타 캡처
#       → Grafana annotation(region) → 대시보드 ${phase}_end 변수 갱신 → 요약 출력
# 재실행해도 파괴적 초기화 없음(데이터·시드 보존). 같은 phase 재측정 시 옛 어노테이션만 정리하고 새로 마킹.
#
# 사용:  measure.sh <before|after>
# 조절 env: APP_PORT(8080) ACT_PORT(9090) RATE(1000) DURATION(60s) IP_POOL(2000)
#           MYSQL_PWD(password) GRAFANA_URL(http://localhost:3000) GRAFANA_AUTH(admin:admin)
#           DASH_UID(qasker-enh-ba) PLG_DASH(대시보드 JSON 경로)
# 사전: 앱(:APP_PORT) 실행 중, q-asker-db 컨테이너, 시드 완료(seed-ids.txt), plg-stack 로컬 스택 기동
# =============================================================
set -uo pipefail

PHASE="${1:-}"
[ "$PHASE" = before ] || [ "$PHASE" = after ] || { echo "사용: measure.sh <before|after>" >&2; exit 64; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB=q-asker-db
DBPW="${MYSQL_PWD:-password}"
APP_PORT="${APP_PORT:-8080}"; ACT_PORT="${ACT_PORT:-9090}"
RATE="${RATE:-1000}"; DURATION="${DURATION:-60s}"; IP_POOL="${IP_POOL:-2000}"
GRAFANA="${GRAFANA_URL:-http://localhost:3000}"; GAUTH="${GRAFANA_AUTH:-admin:admin}"
DASH_UID="${DASH_UID:-qasker-enh-ba}"
PLG_DASH="${PLG_DASH:-$SCRIPT_DIR/../../../../plg-stack/monitoring/grafana/provisioning/dashboards/json/쿼리 튜닝/q-asker-enhancement-before-after.json}"
TMP="${TMPDIR:-/tmp}"

PID=$(lsof -ti "tcp:$APP_PORT" -sTCP:LISTEN 2>/dev/null | head -1)
[ -n "$PID" ] || { echo "🛑 :$APP_PORT 에서 앱을 못 찾음. 앱 먼저 기동." >&2; exit 1; }

mysql_q(){ docker exec "$DB" mysql -uroot -p"$DBPW" -N -e "$1" 2>/dev/null | grep -vi warning; }
app_cputime(){ ps -p "$PID" -o cputime= | tr -d ' '; }
app_heap_b(){ curl -s -m5 "http://localhost:$ACT_PORT/actuator/prometheus" | awk '/jvm_memory_used_bytes.*area="heap"/{s+=$NF} END{printf "%.0f", s}'; }
db_cpu_usec(){ docker exec "$DB" awk '/usage_usec/{print $2}' /sys/fs/cgroup/cpu.stat 2>/dev/null; }
db_mem_b(){ docker exec "$DB" cat /sys/fs/cgroup/memory.current 2>/dev/null; }
gstat(){ mysql_q "SHOW GLOBAL STATUS WHERE Variable_name IN ('Bytes_sent','Innodb_data_read','Innodb_buffer_pool_reads','Innodb_rows_read')" | awk '{print $1"="$2}'; }

echo "▶ [$PHASE] 측정 시작 (app pid=$PID, $RATE TPS $DURATION)"

echo "[1/6] 옛 '$PHASE' 어노테이션 정리"
for id in $(curl -s -u "$GAUTH" "$GRAFANA/api/annotations?tags=$PHASE&limit=100" 2>/dev/null | python3 -c "import sys,json;[print(a['id']) for a in json.load(sys.stdin)]" 2>/dev/null); do
  curl -s -u "$GAUTH" -X DELETE "$GRAFANA/api/annotations/$id" >/dev/null 2>&1
done

echo "[2/6] DB 콜드 재시작"
mysql_q "SET GLOBAL innodb_buffer_pool_dump_at_shutdown=OFF;" >/dev/null
docker exec "$DB" rm -f /var/lib/mysql/ib_buffer_pool 2>/dev/null || true
docker restart "$DB" >/dev/null 2>&1
for i in $(seq 1 40); do curl -s -o /dev/null -m3 "http://localhost:$APP_PORT/actuator/health" 2>/dev/null && break; sleep 1; done
sleep 5

echo "[3/6] before 스냅샷"
AC0=$(app_cputime); AH0=$(app_heap_b); DC0=$(db_cpu_usec); DM0=$(db_mem_b)
gstat > "$TMP/gstat-pre.txt"

echo "[4/6] k6 부하"
S=$(date +%s)
BASE_URL="http://localhost:$APP_PORT" RATE="$RATE" DURATION="$DURATION" EXPLANATION_RATIO=0 IP_POOL="$IP_POOL" \
  k6 run --summary-export="$TMP/k6-$PHASE.json" "$SCRIPT_DIR/load.js" > "$TMP/k6-$PHASE.log" 2>&1
E=$(date +%s)

echo "[5/6] after 스냅샷 + 계산"
AC1=$(app_cputime); AH1=$(app_heap_b); DC1=$(db_cpu_usec); DM1=$(db_mem_b)
gstat > "$TMP/gstat-post.txt"

python3 - "$PHASE" "$AC0" "$AC1" "$DC0" "$DC1" "$AH0" "$AH1" "$DM0" "$DM1" "$TMP/gstat-pre.txt" "$TMP/gstat-post.txt" "$TMP/k6-$PHASE.json" "$E" "$PLG_DASH" "$TMP/measure-$PHASE.env" <<'PY'
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
reqs=json.load(open(k6f))['metrics']['http_reqs']['count']
appct=secs(ac1)-secs(ac0); dbct=(int(dc1)-int(dc0))/1e6
d=lambda k:int(a[k])-int(b[k])
appcpu=appct/reqs*1e6; dbcpu=dbct/reqs*1e6
br=d('Bytes_sent')/reqs/1024; idr=d('Innodb_data_read')/reqs/1024
bpr=d('Innodb_buffer_pool_reads')/reqs; rr=d('Innodb_rows_read')/reqs
print(f"\n==== [{phase}] 읽기 축 측정 결과 ({reqs:.0f} reads) ====")
print(f"  bytes/read            = {br:.2f} KB")
print(f"  앱 JVM CPU-time/read  = {appcpu:.1f} µs   (총 {appct:.1f}s)")
print(f"  MySQL   CPU-time/read = {dbcpu:.1f} µs   (총 {dbct:.1f}s)")
print(f"  Innodb_data_read/read = {idr:.2f} KB")
print(f"  buffer_pool_reads/read= {bpr:.2f} pages")
print(f"  rows_read/read        = {rr:.1f}")
print(f"  앱 힙 {int(ah0)/1048576:.0f}→{int(ah1)/1048576:.0f} MB | DB mem {int(dm0)/1048576:.0f}→{int(dm1)/1048576:.0f} MB")
print("="*50)
# annotate 텍스트용 env
open(envf,'w').write(f"BR={br:.2f}\nACPU={appcpu:.0f}\nDCPU={dbcpu:.0f}\n")
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

echo "[6/6] Grafana annotation"
# shellcheck disable=SC1090
. "$TMP/measure-$PHASE.env"
bash "$SCRIPT_DIR/annotate.sh" "$PHASE" "$S" "$E" "read $RATE TPS cold (bytes/read ${BR}KB, appCPU ${ACPU}µs, dbCPU ${DCPU}µs)"
echo "✅ [$PHASE] 완료. 구간 $((E-S))s (epoch $S~$E). Grafana 대시보드 $DASH_UID 새로고침."
