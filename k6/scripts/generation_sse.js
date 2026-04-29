import http from "k6/http";
import { check } from "k6";
import { Trend, Counter, Rate } from "k6/metrics";
import sse from "k6/x/sse";

// ──────────────────────────────────────────────
// 시나리오 3: Before/After 비교
// develop (기존 call) vs ICC-282-prompt (streaming)
// 동일 조건: 같은 PDF, quizCount=15, 5 VUs, 5분
// ──────────────────────────────────────────────

// 환경 변수
const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL) {
  throw new Error("BASE_URL is not defined");
}

// 커스텀 메트릭 - 비교 포인트 3가지
const ttfq = new Trend("ttfq", true); // TTFQ: POST → 첫 퀴즈 데이터(created) 수신 (streaming이 빨라야 함)
const ttComplete = new Trend("tt_complete", true); // TTComplete: POST → 전체 완료 (비슷해야 함)
const errorRate = new Rate("error_rate"); // 에러율 차이

const sseEventCount = new Counter("sse_event_count");

export const options = {
  scenarios: {
    before_after_comparison: {
      executor: "constant-vus",
      vus: 5,
      duration: "15m",
    },
  },

  thresholds: {
    ttfq: ["avg<30000"], // TTFQ 평균 30초 이내
    tt_complete: ["avg<120000"], // TTComplete 평균 2분 이내
    error_rate: ["rate<0.05"], // 에러율 5% 미만
  },
};

// 고정 조건: 동일 PDF, quizCount=15, 동일 pageNumbers
const TEST_DATA = {
  uploadedUrl:
    "https://files.q-asker.com/aa398b50-1629-4ee5-99e7-ffc46f12af1f.pdf",
  quizCount: 15,
  quizType: "MULTIPLE",
  difficultyType: "STRATEGIC",
  pageNumbers: [
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
    21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38,
    39, 40,
  ],
};

export default function () {
  const sessionId = crypto.randomUUID();
  const tag = sessionId.substring(0, 8);

  // 1. POST 퀴즈 생성 요청 (TTFQ 측정 기준점)
  const postStartTime = Date.now();
  const payload = JSON.stringify({
    ...TEST_DATA,
    sessionId: sessionId,
  });

  const postRes = http.post(`${BASE_URL}/generation`, payload, {
    headers: { "Content-Type": "application/json" },
    tags: { name: "POST_generation" },
  });

  const isPostSuccess = check(postRes, {
    "POST 요청 성공 (202)": (r) => r.status === 202,
  });

  if (!isPostSuccess) {
    errorRate.add(true);
    console.error(`[${tag}] POST 실패: status=${postRes.status}`);
    return;
  }

  // 2. SSE 구독 + TTFQ / TTComplete 측정 (POST 시점 기준)
  let firstEventTime = 0;
  let createdCount = 0;
  let eventCount = 0;
  let completed = false;

  const response = sse.open(
    `${BASE_URL}/generation/${sessionId}/stream`,
    { method: "GET" },
    function (client) {
      client.on("event", function (event) {
        eventCount++;
        sseEventCount.add(1);

        // TTFQ: 두 번째 created 이벤트 수신 시간 (첫 번째는 SSE 연결 전 버퍼링된 것)
        if (event.name === "created") {
          createdCount++;
          if (createdCount === 2 && firstEventTime === 0) {
            firstEventTime = Date.now();
            const ttfqValue = firstEventTime - postStartTime;
            ttfq.add(ttfqValue);
            console.log(`[${tag}] TTFQ: ${ttfqValue}ms`);
          }
        }

        // TTComplete: complete 이벤트 수신 시간
        if (event.name === "complete") {
          completed = true;
          const total = Date.now() - postStartTime;
          ttComplete.add(total);
          console.log(`[${tag}] TTComplete: ${total}ms (이벤트 ${eventCount}개, created ${createdCount}개)`);
          client.close();
        }

        // error-finish 이벤트 감지
        if (event.name === "error-finish") {
          console.error(`[${tag}] error-finish 수신: ${event.data}`);
          client.close();
        }
      });

      client.on("error", function (err) {
        console.error(`[${tag}] SSE 에러: ${err}`);
        client.close();
      });
    }
  );

  // SSE 종료 후 상태 로깅
  const elapsed = Date.now() - postStartTime;
  if (!completed) {
    console.warn(`[${tag}] 미완료 종료: ${elapsed}ms 경과, 이벤트 ${eventCount}개, created ${createdCount}개`);
  }

  const isSseSuccess = check(response, {
    "SSE 연결 성공 (200)": (r) => r && r.status === 200,
  });

  // 에러율 기록
  errorRate.add(!isSseSuccess || !completed);
}
