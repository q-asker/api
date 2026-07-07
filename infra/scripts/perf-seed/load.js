// =============================================================
// perf-seed/load.js — before/after 성능 측정용 k6 부하 스크립트 (Q3/Q10, FR-013 / SC-006/008)
// 대상: 시드된 seed- 세트(id 1000000~)의 풀이 조회 경로.
//   주:  GET /problem-set/{hashid}   (permitAll, 핵심 측정 경로)
//   보조: GET /explanation/{hashid}  (permitAll, READ 레이트리밋 → 고부하 시 429 가능)
//
// URL의 {hashid}는 raw 세트 id(1000000~)를 앱과 동일 salt/min-length로 hashids 인코딩한 값이다.
// salt가 Jasypt ENC로 저장돼 있어 k6가 직접 인코딩할 수 없으므로, 사전에 gen-seed-ids.mjs로
// seed-ids.txt(한 줄당 hashid)를 생성해 두고 이 스크립트가 읽는다.
//
// 실행 (quickstart §4-2):
//   node gen-seed-ids.mjs                 # seed-ids.txt 생성 (HASHID_SALT 필요)
//   BASE_URL=http://localhost:8080 k6 run load.js
// 조절 env: BASE_URL, IDS_FILE(기본 ./seed-ids.txt), VUS(기본 20), DURATION(기본 2m),
//           EXPLANATION_RATIO(기본 0.2, 0이면 problem-set만),
//           IP_POOL(기본 0=rate limit 준수; >0이면 합성 X-Forwarded-For 분산으로 per-IP 버킷 우회 — 부하 p95 측정용)
// =============================================================

import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const IDS_FILE = __ENV.IDS_FILE || './seed-ids.txt';
const VUS = parseInt(__ENV.VUS || '20', 10);
const DURATION = __ENV.DURATION || '2m';
const EXPLANATION_RATIO = parseFloat(__ENV.EXPLANATION_RATIO || '0.2');
// IP_POOL>0: 요청마다 합성 X-Forwarded-For를 분산해 per-IP rate limit 버킷(READ 60/min)을 분산 —
// 재시작·config 변경 없이 하드 부하로 실제 p95 측정용(로컬 perf 전용). 0이면 정직하게 rate limit 준수.
const IP_POOL = parseInt(__ENV.IP_POOL || '0', 10);
// RATE>0: constant-arrival-rate(도착률=TPS 고정, open model) — before/after를 동일 부하로 비교(권장).
//         0이면 VU 모드(constant-vus). RATE 모드에서 preAllocatedVUs≈RATE×p95(s), maxVUs는 지연 급증 대비 상한.
const RATE = parseInt(__ENV.RATE || '0', 10);
const PRE_VUS = parseInt(__ENV.PRE_VUS || '100', 10);
const MAX_VUS = parseInt(__ENV.MAX_VUS || '400', 10);

function reqParams(name) {
  const p = { tags: { name } };
  if (IP_POOL > 0) {
    const n = 1 + Math.floor(Math.random() * IP_POOL);
    p.headers = { 'X-Forwarded-For': `10.${(n >> 16) & 255}.${(n >> 8) & 255}.${n & 255}` };
  }
  return p;
}

// 초기화 컨텍스트에서 hashid 목록을 1회 로드해 VU 간 공유(메모리 절약)
const IDS = new SharedArray('seed-hashids', function () {
  const raw = open(IDS_FILE);
  return raw.split('\n').map((s) => s.trim()).filter((s) => s.length > 0);
});

const problemSetDuration = new Trend('problem_set_duration', true);
const explanationDuration = new Trend('explanation_duration', true);
const rateLimited = new Counter('rate_limited_429');

export const options = {
  scenarios:
    RATE > 0
      ? {
          read: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: PRE_VUS,
            maxVUs: MAX_VUS,
          },
        }
      : { read: { executor: 'constant-vus', vus: VUS, duration: DURATION } },
  thresholds: {
    // 게이트가 아니라 요약 출력용 — before 기준선을 이 값으로 기록한다.
    problem_set_duration: ['p(95)>=0'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  if (IDS.length === 0) {
    throw new Error(`${IDS_FILE} 가 비어 있음. 먼저 gen-seed-ids.mjs로 hashid 목록을 생성하세요.`);
  }
  console.log(`대상 세트 ${IDS.length}개, BASE_URL=${BASE_URL}, VUS=${VUS}, DURATION=${DURATION}`);
}

export default function () {
  const id = IDS[Math.floor(Math.random() * IDS.length)];

  // 주 경로: 풀이 시작 조회
  const psRes = http.get(`${BASE_URL}/problem-set/${id}`, reqParams('problem-set'));
  problemSetDuration.add(psRes.timings.duration);
  check(psRes, { 'problem-set 200': (r) => r.status === 200 });

  // 보조 경로: 해설 조회 (일부 비율만 — READ 레이트리밋 429 회피)
  if (EXPLANATION_RATIO > 0 && Math.random() < EXPLANATION_RATIO) {
    const exRes = http.get(`${BASE_URL}/explanation/${id}`, reqParams('explanation'));
    explanationDuration.add(exRes.timings.duration);
    if (exRes.status === 429) {
      rateLimited.add(1);
    }
    check(exRes, { 'explanation 200|429': (r) => r.status === 200 || r.status === 429 });
  }
}
