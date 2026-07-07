// =============================================================
// perf-seed/write-load.js — 쓰기 축(dirty tracking) before/after 측정용 k6 부하 스크립트
//
// 대상: DevWriteBenchController (Profile=local, RateLimit=NONE) — **실제 Gemini 호출 없음**.
//   saveExplanation(=Problem 재로드 → updateExplanation → flush, dirty-tracking 경로)만 HTTP로 구동해
//   실제 MySQL에 쓰기 부하를 걸고 앱/DB CPU-time·RAM·InnoDB write 델타를 측정한다.
//   기존 JUnit 벤치(DirtyTrackingWriteBenchmark/DirtyCheckScanBenchmark)를 부하 형태로 옮긴 것.
//
//   주:  POST /dev/bench/explanation?setId=&number=     (단건 dirty-tracking 경로, 기본)
//   배치: POST /dev/bench/explanations?setId=&count=     (N건 단일 트랜잭션 — 커밋·fsync N→1 비교, WRITE_MODE=batch)
//
// 대상 세트: perf-seed 시드 세트(id = SET_ID_BASE + [0, N_SETS)). seed.sql 기준
//   set_id = @base(기본 1000000) + set_seq, number = 1..PROBLEMS_PER_SET(16).
//   N_SETS = CEIL(scale * 66684 / 16)  (scale 10 → 41678). raw id라 hashid 인코딩 불필요.
//
// 실행 (measure-write.sh가 자동 호출):
//   BASE_URL=http://localhost:8080 RATE=1000 DURATION=60s k6 run write-load.js
// 조절 env: BASE_URL, RATE(도착률 TPS, 0이면 VU 모드), VUS(기본 20), DURATION(기본 60s),
//           SET_ID_BASE(1000000), N_SETS(41678), PROBLEMS_PER_SET(16),
//           WRITE_MODE(single|batch, 기본 single), BATCH_COUNT(기본 16),
//           PRE_VUS(100), MAX_VUS(400)
// =============================================================

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = parseInt(__ENV.VUS || '20', 10);
const DURATION = __ENV.DURATION || '60s';

const SET_ID_BASE = parseInt(__ENV.SET_ID_BASE || '1000000', 10);
const N_SETS = parseInt(__ENV.N_SETS || '41678', 10); // scale 10 기준. 다른 scale이면 CEIL(scale*66684/16)로 지정
const PROBLEMS_PER_SET = parseInt(__ENV.PROBLEMS_PER_SET || '16', 10);

const WRITE_MODE = (__ENV.WRITE_MODE || 'single').toLowerCase(); // single | batch
const BATCH_COUNT = parseInt(__ENV.BATCH_COUNT || '16', 10);

// RATE>0: constant-arrival-rate(도착률=TPS 고정, open model) — before/after를 동일 부하로 비교(권장).
//         0이면 VU 모드(constant-vus).
const RATE = parseInt(__ENV.RATE || '0', 10);
const PRE_VUS = parseInt(__ENV.PRE_VUS || '100', 10);
const MAX_VUS = parseInt(__ENV.MAX_VUS || '400', 10);

const writeDuration = new Trend('write_duration', true);
const writeFailed = new Counter('write_failed');

export const options = {
  scenarios:
    RATE > 0
      ? {
          write: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: PRE_VUS,
            maxVUs: MAX_VUS,
          },
        }
      : { write: { executor: 'constant-vus', vus: VUS, duration: DURATION } },
  thresholds: {
    // 게이트가 아니라 요약 출력용 — before 기준선을 이 값으로 기록한다.
    write_duration: ['p(95)>=0'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  if (N_SETS <= 0) {
    throw new Error('N_SETS는 1 이상이어야 함 (CEIL(scale*66684/16)).');
  }
  console.log(
    `쓰기 부하: mode=${WRITE_MODE}, 세트 ${N_SETS}개(id ${SET_ID_BASE}~${SET_ID_BASE + N_SETS - 1}), ` +
      `BASE_URL=${BASE_URL}, RATE=${RATE || 'VU:' + VUS}, DURATION=${DURATION}`,
  );
}

function pickSetId() {
  return SET_ID_BASE + Math.floor(Math.random() * N_SETS);
}

export default function () {
  const setId = pickSetId();

  if (WRITE_MODE === 'batch') {
    // 배치: count건을 saveExplanations(단일 트랜잭션)로 저장 — 단건 대비 커밋·fsync N→1
    const res = http.post(
      `${BASE_URL}/dev/bench/explanations?setId=${setId}&count=${BATCH_COUNT}`,
      null,
      { tags: { name: 'explanations-batch' } },
    );
    writeDuration.add(res.timings.duration);
    if (!check(res, { 'explanations 2xx': (r) => r.status >= 200 && r.status < 300 })) {
      writeFailed.add(1);
    }
    return;
  }

  // 단건: dirty-tracking 경로(재로드 → updateExplanation → flush)
  const number = 1 + Math.floor(Math.random() * PROBLEMS_PER_SET);
  const res = http.post(
    `${BASE_URL}/dev/bench/explanation?setId=${setId}&number=${number}`,
    null,
    { tags: { name: 'explanation' } },
  );
  writeDuration.add(res.timings.duration);
  if (!check(res, { 'explanation 2xx': (r) => r.status >= 200 && r.status < 300 })) {
    writeFailed.add(1);
  }
}
