// ──────────────────────────────────────────────
// 시나리오: 전체 API 스캔 부하테스트 (BEFORE)
// 목적: 시딩 후 읽기/쓰기/잠금 API 성능 기준선 측정
// 대상: problem-set, explanation, history, auth/refresh
// 트레이드오프: 인덱스 추가 시 읽기 ↑ / 쓰기 ↓ 양쪽 측정
// ──────────────────────────────────────────────
import http from "k6/http";
import { check, group } from "k6";
import { Trend, Rate } from "k6/metrics";
import Hashids from "https://cdn.jsdelivr.net/npm/hashids@2.3.0/+esm";

const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL) throw new Error("BASE_URL is not defined");

const JWT_TOKEN = __ENV.JWT_TOKEN || "";
const HASHID_SALT = "5205177c-7650-4b15-ae13-cc7a7a216b16";
const HASHID_MIN_LENGTH = 8;
const hashids = new Hashids(HASHID_SALT, HASHID_MIN_LENGTH);

const PS_ID_MAX = 164600;

// ── 읽기 API 메트릭 ──
const problemSetLatency = new Trend("read_problemset_latency", true);
const explanationLatency = new Trend("read_explanation_latency", true);
const historyListLatency = new Trend("read_history_list_latency", true);
const historyDetailLatency = new Trend("read_history_detail_latency", true);
const historyEssayLatency = new Trend("read_history_essay_latency", true);

// ── 쓰기 API 메트릭 ──
const historyInitLatency = new Trend("write_history_init_latency", true);
const historyTitleLatency = new Trend("write_history_title_latency", true);

// ── 잠금 API 메트릭 ──
const refreshLatency = new Trend("lock_refresh_latency", true);

const errorRate = new Rate("error_rate");

export const options = {
  scenarios: {
    load_test: {
      executor: "constant-vus",
      vus: 50,
      duration: "30s",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<3000"],
  },
};

function encodedPsId() {
  return hashids.encode(Math.floor(Math.random() * PS_ID_MAX) + 1);
}

function authHeaders() {
  return {
    Authorization: `Bearer ${JWT_TOKEN}`,
    "Content-Type": "application/json",
  };
}

// 각 VU별 고유 시퀀스 (history init 중복 방지)
let seq = 0;

export default function () {
  // ═══════════════════════════════════════
  // 읽기 API
  // ═══════════════════════════════════════

  // 1. GET /problem-set/{id}
  group("READ: problem-set", () => {
    const res = http.get(`${BASE_URL}/problem-set/${encodedPsId()}`, {
      tags: { name: "GET /problem-set/{id}" },
    });
    problemSetLatency.add(res.timings.duration);
    const ok = check(res, {
      "problem-set 200|404": (r) => r.status === 200 || r.status === 404,
    });
    errorRate.add(!ok);
  });

  // 2. GET /explanation/{id}
  group("READ: explanation", () => {
    const res = http.get(`${BASE_URL}/explanation/${encodedPsId()}`, {
      tags: { name: "GET /explanation/{id}" },
    });
    explanationLatency.add(res.timings.duration);
    const ok = check(res, {
      "explanation 200|404": (r) => r.status === 200 || r.status === 404,
    });
    errorRate.add(!ok);
  });

  // ═══════════════════════════════════════
  // 잠금 API (SELECT FOR UPDATE)
  // ═══════════════════════════════════════

  // 3. POST /auth/refresh
  group("LOCK: auth/refresh", () => {
    const idx = Math.floor(Math.random() * 4911) + 1;
    const res = http.post(`${BASE_URL}/auth/refresh`, null, {
      headers: {
        "Content-Type": "application/json",
        Cookie: `refresh_token=dummy_token_${idx}`,
      },
      tags: { name: "POST /auth/refresh" },
    });
    refreshLatency.add(res.timings.duration);
    const ok = check(res, {
      "refresh not 500": (r) => r.status !== 500,
    });
    errorRate.add(!ok);
  });

  // ═══════════════════════════════════════
  // 인증 필요 API (JWT_TOKEN 있을 때만)
  // ═══════════════════════════════════════
  if (!JWT_TOKEN) return;

  // 4. GET /history (읽기)
  group("READ: history list", () => {
    const page = Math.floor(Math.random() * 10);
    const res = http.get(`${BASE_URL}/history?page=${page}&size=10`, {
      headers: authHeaders(),
      tags: { name: "GET /history" },
    });
    historyListLatency.add(res.timings.duration);
    const ok = check(res, {
      "history list 200": (r) => r.status === 200,
    });
    errorRate.add(!ok);
  });

  // 5. GET /history/{historyId} (읽기)
  group("READ: history detail", () => {
    const hid = hashids.encode(Math.floor(Math.random() * 35000) + 1);
    const res = http.get(`${BASE_URL}/history/${hid}`, {
      headers: authHeaders(),
      tags: { name: "GET /history/{id}" },
    });
    historyDetailLatency.add(res.timings.duration);
    const ok = check(res, {
      "history detail 200|403|404": (r) =>
        r.status === 200 || r.status === 403 || r.status === 404,
    });
    errorRate.add(!ok);
  });

  // 6. GET /history/{historyId}/essay (읽기 - 상관 서브쿼리)
  group("READ: history essay", () => {
    const hid = hashids.encode(Math.floor(Math.random() * 35000) + 1);
    const res = http.get(`${BASE_URL}/history/${hid}/essay`, {
      headers: authHeaders(),
      tags: { name: "GET /history/{id}/essay" },
    });
    historyEssayLatency.add(res.timings.duration);
    const ok = check(res, {
      "essay 200|403|404": (r) =>
        r.status === 200 || r.status === 403 || r.status === 404,
    });
    errorRate.add(!ok);
  });

  // ═══════════════════════════════════════
  // 쓰기 API (INSERT/UPDATE)
  // ═══════════════════════════════════════

  // 7. POST /history/init (INSERT)
  group("WRITE: history init", () => {
    seq++;
    const uniquePsId = encodedPsId();
    const body = JSON.stringify({
      problemSetId: uniquePsId,
      title: `k6_test_${__VU}_${seq}`,
    });
    const res = http.post(`${BASE_URL}/history/init`, body, {
      headers: authHeaders(),
      tags: { name: "POST /history/init" },
    });
    historyInitLatency.add(res.timings.duration);
    // 201(성공) 또는 409(이미 존재) 모두 DB 쿼리는 실행됨
    const ok = check(res, {
      "history init 201|409": (r) => r.status === 201 || r.status === 409,
    });
    errorRate.add(!ok);
  });

  // 8. PATCH /history/{historyId}/title (UPDATE)
  group("WRITE: history title", () => {
    const hid = hashids.encode(Math.floor(Math.random() * 35000) + 1);
    const body = JSON.stringify({ title: `updated_${Date.now()}` });
    const res = http.patch(`${BASE_URL}/history/${hid}/title`, body, {
      headers: authHeaders(),
      tags: { name: "PATCH /history/{id}/title" },
    });
    historyTitleLatency.add(res.timings.duration);
    // 204(성공) 또는 403/404(본인 아님/없음) 모두 DB 조회는 실행됨
    const ok = check(res, {
      "history title 204|403|404": (r) =>
        r.status === 204 || r.status === 403 || r.status === 404,
    });
    errorRate.add(!ok);
  });
}
