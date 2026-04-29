// ──────────────────────────────────────────────
// 시나리오: problem 테이블 쿼리 튜닝 BEFORE 부하테스트
// 목적: 최적화 전 읽기/쓰기 API 성능 기준선 측정
// 가중치: 프로덕션 Prometheus 120일 트래픽 비율 기반
// ID pool: setup()에서 서버 API를 호출하여 실제 존재하는 encoded ID 수집
// ──────────────────────────────────────────────
import http from "k6/http";
import { check, group } from "k6";
import { Trend, Rate, Counter } from "k6/metrics";
import { SharedArray } from "k6/data";

// ── 환경 변수 ──
const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL) throw new Error("BASE_URL is required (-e BASE_URL=http://localhost:9081)");
const JWT_TOKEN = __ENV.JWT_TOKEN || "";

// ── ID pool (사전 생성된 encoded ID 목록) ──
const idPool = JSON.parse(open("../data/id-pool.json"));
const PS_IDS = idPool.psIds;
const H_IDS = idPool.hIds;

// ── 커스텀 메트릭 ──
const mProblemSet = new Trend("read_problem_set", true);
const mHistoryCheck = new Trend("read_history_check", true);
const mExplanation = new Trend("read_explanation", true);
const mHistoryList = new Trend("read_history_list", true);
const mHistoryDetail = new Trend("read_history_detail", true);
const mHistorySave = new Trend("write_history_save", true);
const mGeneration = new Trend("write_generation", true);
const mHistoryInit = new Trend("write_history_init", true);
const mPsTitle = new Trend("write_ps_title", true);
const mHistoryTitle = new Trend("write_history_title", true);
const errorRate = new Rate("error_rate");
const reqCount = new Counter("total_requests");

// ── 테스트 옵션 ──
export const options = {
  scenarios: {
    load_test: {
      executor: "constant-vus",
      vus: 10,
      duration: "30s",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<3000"],
    error_rate: ["rate<0.1"],
  },
};

// ── setup: ID pool 확인 ──
export function setup() {
  console.log(`[setup] ID pool — psIds: ${PS_IDS.length}, hIds: ${H_IDS.length}`);
  // 서버 health check
  const res = http.get(`${BASE_URL}/status`);
  console.log(`[setup] 서버 상태: ${res.status}`);
  return {};
}

// ── 유틸리티 ──
function authHeaders() {
  return {
    Authorization: `Bearer ${JWT_TOKEN}`,
    "Content-Type": "application/json",
  };
}

function randomFrom(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// ── 프로덕션 트래픽 비율 기반 가중치 ──
const weights = [
  { weight: 38.1, name: "readProblemSet" },
  { weight: 8.5, name: "readHistoryCheck" },
  { weight: 6.6, name: "writeHistorySave" },
  { weight: 6.4, name: "writeGeneration" },
  { weight: 5.4, name: "writeHistoryInit" },
  { weight: 3.9, name: "readExplanation" },
  { weight: 2.9, name: "readHistoryList" },
  { weight: 0.8, name: "readHistoryDetail" },
  { weight: 0.2, name: "writePsTitle" },
  { weight: 0.02, name: "writeHistoryTitle" },
];

const totalWeight = weights.reduce((s, w) => s + w.weight, 0);
const cumulative = [];
let acc = 0;
for (const w of weights) {
  acc += w.weight / totalWeight;
  cumulative.push({ threshold: acc, name: w.name });
}

function pickScenario() {
  const r = Math.random();
  for (const c of cumulative) {
    if (r < c.threshold) return c.name;
  }
  return cumulative[cumulative.length - 1].name;
}

// ── VU별 시퀀스 ──
let seq = 0;

// ── 메인 함수 ──
export default function () {
  const scenario = pickScenario();

  switch (scenario) {
    case "readProblemSet":
      return doReadProblemSet();
    case "readHistoryCheck":
      return doReadHistoryCheck();
    case "writeHistorySave":
      return doWriteHistorySave();
    case "writeGeneration":
      return doWriteGeneration();
    case "writeHistoryInit":
      return doWriteHistoryInit();
    case "readExplanation":
      return doReadExplanation();
    case "readHistoryList":
      return doReadHistoryList();
    case "readHistoryDetail":
      return doReadHistoryDetail();
    case "writePsTitle":
      return doWritePsTitle();
    case "writeHistoryTitle":
      return doWriteHistoryTitle();
  }
}

// ═══════════════════════════════════════
// 읽기 API
// ═══════════════════════════════════════

function doReadProblemSet() {
  group("READ: problem-set", () => {
    const id = randomFrom(PS_IDS);
    const res = http.get(`${BASE_URL}/problem-set/${id}`, {
      tags: { name: "GET /problem-set/{id}" },
    });
    mProblemSet.add(res.timings.duration);
    reqCount.add(1);
    const ok = check(res, { "status 200": (r) => r.status === 200 });
    errorRate.add(!ok);
  });
}

function doReadHistoryCheck() {
  if (!JWT_TOKEN) return doReadProblemSet();
  group("READ: history check", () => {
    const id = randomFrom(PS_IDS);
    const res = http.get(`${BASE_URL}/history/check/${id}`, {
      headers: authHeaders(),
      tags: { name: "GET /history/check/{id}" },
    });
    mHistoryCheck.add(res.timings.duration);
    reqCount.add(1);
    const ok = check(res, { "status 200": (r) => r.status === 200 });
    errorRate.add(!ok);
  });
}

function doReadExplanation() {
  group("READ: explanation", () => {
    const id = randomFrom(PS_IDS);
    const res = http.get(`${BASE_URL}/explanation/${id}`, {
      tags: { name: "GET /explanation/{id}" },
    });
    mExplanation.add(res.timings.duration);
    reqCount.add(1);
    const ok = check(res, { "status 200": (r) => r.status === 200 });
    errorRate.add(!ok);
  });
}

function doReadHistoryList() {
  if (!JWT_TOKEN) return doReadProblemSet();
  group("READ: history list", () => {
    const page = Math.floor(Math.random() * 5);
    const res = http.get(`${BASE_URL}/history?page=${page}&size=20`, {
      headers: authHeaders(),
      tags: { name: "GET /history" },
    });
    mHistoryList.add(res.timings.duration);
    reqCount.add(1);
    const ok = check(res, { "status 200": (r) => r.status === 200 });
    errorRate.add(!ok);
  });
}

function doReadHistoryDetail() {
  if (!JWT_TOKEN) return doReadProblemSet();
  group("READ: history detail", () => {
    const id = randomFrom(H_IDS);
    const res = http.get(`${BASE_URL}/history/${id}`, {
      headers: authHeaders(),
      tags: { name: "GET /history/{id}" },
    });
    mHistoryDetail.add(res.timings.duration);
    reqCount.add(1);
    const ok = check(res, {
      "status 200|403": (r) => r.status === 200 || r.status === 403,
    });
    errorRate.add(!ok);
  });
}

// ═══════════════════════════════════════
// 쓰기 API
// ═══════════════════════════════════════

function doWriteHistorySave() {
  if (!JWT_TOKEN) return doReadProblemSet();
  group("WRITE: history save", () => {
    seq++;
    const body = JSON.stringify({
      problemSetId: randomFrom(PS_IDS),
      title: `k6_save_${__VU}_${seq}`,
      userAnswers: [
        { number: 1, userAnswer: 2, inReview: false, textAnswer: null },
        { number: 2, userAnswer: 1, inReview: false, textAnswer: null },
      ],
      score: 50,
      totalTime: "00:01:30",
    });
    const res = http.post(`${BASE_URL}/history`, body, {
      headers: authHeaders(),
      tags: { name: "POST /history" },
    });
    mHistorySave.add(res.timings.duration);
    reqCount.add(1);
    // 201(성공) 또는 409(이미 존재 → DB 조회는 실행됨)
    const ok = check(res, {
      "status 201|409": (r) => r.status === 201 || r.status === 409,
    });
    errorRate.add(!ok);
  });
}

function doWriteGeneration() {
  group("WRITE: generation (mock)", () => {
    seq++;
    const body = JSON.stringify({
      sessionId: `k6-mock-${__VU}-${seq}-${Date.now()}`,
      uploadedUrl: "gs://q-asker-ai-files/mock-test.pdf",
      title: `k6_gen_${__VU}_${seq}`,
      quizCount: 5,
      quizType: "MULTIPLE",
      pageNumbers: [1, 2, 3],
    });
    const res = http.post(`${BASE_URL}/generation`, body, {
      headers: authHeaders(),
      tags: { name: "POST /generation" },
    });
    mGeneration.add(res.timings.duration);
    reqCount.add(1);
    const ok = check(res, {
      "status 202|429": (r) => r.status === 202 || r.status === 429,
    });
    errorRate.add(!ok);
  });
}

function doWriteHistoryInit() {
  if (!JWT_TOKEN) return doReadProblemSet();
  group("WRITE: history init", () => {
    seq++;
    const body = JSON.stringify({
      problemSetId: randomFrom(PS_IDS),
      title: `k6_init_${__VU}_${seq}`,
    });
    const res = http.post(`${BASE_URL}/history/init`, body, {
      headers: authHeaders(),
      tags: { name: "POST /history/init" },
    });
    mHistoryInit.add(res.timings.duration);
    reqCount.add(1);
    const ok = check(res, {
      "status 201|409": (r) => r.status === 201 || r.status === 409,
    });
    errorRate.add(!ok);
  });
}

function doWritePsTitle() {
  if (!JWT_TOKEN) return doReadProblemSet();
  group("WRITE: problem-set title", () => {
    const body = JSON.stringify({ title: `k6_ps_${Date.now()}` });
    const res = http.patch(
      `${BASE_URL}/problem-set/${randomFrom(PS_IDS)}/title`,
      body,
      { headers: authHeaders(), tags: { name: "PATCH /problem-set/{id}/title" } }
    );
    mPsTitle.add(res.timings.duration);
    reqCount.add(1);
    const ok = check(res, {
      "status 200|403": (r) => r.status === 200 || r.status === 403,
    });
    errorRate.add(!ok);
  });
}

function doWriteHistoryTitle() {
  if (!JWT_TOKEN) return doReadProblemSet();
  group("WRITE: history title", () => {
    const body = JSON.stringify({ title: `k6_ht_${Date.now()}` });
    const res = http.patch(
      `${BASE_URL}/history/${randomFrom(H_IDS)}/title`,
      body,
      { headers: authHeaders(), tags: { name: "PATCH /history/{id}/title" } }
    );
    mHistoryTitle.add(res.timings.duration);
    reqCount.add(1);
    const ok = check(res, {
      "status 204|403": (r) => r.status === 204 || r.status === 403,
    });
    errorRate.add(!ok);
  });
}
