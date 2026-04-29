// ──────────────────────────────────────────────
// 시나리오: refresh_token findByRtHash 부하테스트 (BEFORE) — 멀티 시나리오 + 확장 시딩
//
// 목적: rt_hash 인덱스 부재 + PESSIMISTIC_WRITE 환경에서
//       풀스캔 + 갭락이 동시 트래픽(읽기 부하 공존) 환경에서
//       어떻게 connection pool / CPU / buffer pool / 락 경합을 일으키는지 측정.
//
// 시딩 환경 (Step 2 확장 후):
//   - user           : 10,091
//   - problem_set    : 262,489 (신규 13650 ~ 273648)
//   - problem        : ~1,037,000
//   - quiz_history   : 86,431
//   - refresh_token  : 10,000 (모두 만료, rt_hash=SHA2('seed_token_${idx}',256))
//
// 동시 부하: refresh 10 VU + problem_set 5 VU + explanation 5 VU = 20 VU
//   → Hikari pool(기본 10) 초과 → connection wait 명시적 노출
//   → 26만 problem_set 페이지 분산 → buffer pool 경합 명시적 노출
// ──────────────────────────────────────────────
import http from "k6/http";
import { check } from "k6";
import { Trend, Rate, Counter } from "k6/metrics";
import Hashids from "https://cdn.jsdelivr.net/npm/hashids@2.3.0/+esm";

const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL) {
  throw new Error("BASE_URL is not defined (예: BASE_URL=http://localhost:9081)");
}

// ── 시딩 풀 ──
const TOKEN_POOL_SIZE = 10000;
const TOKENS_PER_VU = 1000;

// ── problem_set/explanation ID 분배: 1 ~ 273648 (확장 시딩 후 거의 모두 hit) ──
const PS_ID_MAX = 273648;
const HASHID_SALT = "5205177c-7650-4b15-ae13-cc7a7a216b16";
const HASHID_MIN_LENGTH = 8;
const hashids = new Hashids(HASHID_SALT, HASHID_MIN_LENGTH);

function randomEncodedPsId() {
  const realId = Math.floor(Math.random() * PS_ID_MAX) + 1;
  return hashids.encode(realId);
}

// ── 메트릭 ──
const refreshLatency = new Trend("refresh_latency", true);
const problemSetLatency = new Trend("problem_set_latency", true);
const explanationLatency = new Trend("explanation_latency", true);

const errorRate = new Rate("error_rate");
const refreshErrorRate = new Rate("refresh_error_rate");

const refreshStatus200 = new Counter("refresh_status_200");
const refreshStatus401 = new Counter("refresh_status_401");
const refreshStatusOther = new Counter("refresh_status_other");

// ── 시나리오 정의 ──
export const options = {
  scenarios: {
    refresh_lock: {
      executor: "constant-vus",
      vus: 10,
      duration: "30s",
      exec: "refreshScenario",
      tags: { scenario: "refresh_lock" },
    },
    problem_set_read: {
      executor: "constant-vus",
      vus: 5,
      duration: "30s",
      exec: "problemSetScenario",
      tags: { scenario: "problem_set_read" },
    },
    explanation_read: {
      executor: "constant-vus",
      vus: 5,
      duration: "30s",
      exec: "explanationScenario",
      tags: { scenario: "explanation_read" },
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<10000"],
    refresh_error_rate: ["rate<1.0"],
  },
};

let counter = 0;

// ── 시나리오 1: refresh (잠금) ──
export function refreshScenario() {
  const offset = (__VU - 1) * TOKENS_PER_VU;
  const idx = (offset + (counter % TOKENS_PER_VU)) % TOKEN_POOL_SIZE + 1;
  counter++;

  const tokenPlain = `seed_token_${idx}`;
  const params = {
    headers: {
      "Content-Type": "application/json",
      Cookie: `refresh_token=${tokenPlain}`,
    },
    tags: { name: "POST /auth/refresh" },
  };

  const res = http.post(`${BASE_URL}/auth/refresh`, null, params);

  refreshLatency.add(res.timings.duration);
  if (res.status === 200) refreshStatus200.add(1);
  else if (res.status === 401) refreshStatus401.add(1);
  else refreshStatusOther.add(1);

  const passed = check(res, {
    "refresh status 200|401": (r) => r.status === 200 || r.status === 401,
  });
  refreshErrorRate.add(!passed);
  errorRate.add(!passed);
}

// ── 시나리오 2: problem-set 읽기 (PK lookup, 26만 ID 분산 → buffer pool 경합) ──
export function problemSetScenario() {
  const psId = randomEncodedPsId();
  const res = http.get(`${BASE_URL}/problem-set/${psId}`, {
    tags: { name: "GET /problem-set/{id}" },
  });
  problemSetLatency.add(res.timings.duration);

  const passed = check(res, {
    "problem-set 200|404": (r) => r.status === 200 || r.status === 404,
  });
  errorRate.add(!passed);
}

// ── 시나리오 3: explanation 읽기 (PK + 연관) ──
export function explanationScenario() {
  const psId = randomEncodedPsId();
  const res = http.get(`${BASE_URL}/explanation/${psId}`, {
    tags: { name: "GET /explanation/{id}" },
  });
  explanationLatency.add(res.timings.duration);

  const passed = check(res, {
    "explanation 200|404": (r) => r.status === 200 || r.status === 404,
  });
  errorRate.add(!passed);
}
