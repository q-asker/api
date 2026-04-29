// ──────────────────────────────────────────────
// 시나리오: k6 동작 확인 스모크 테스트
// 로컬 서버에 간단한 GET 요청을 보내 k6가 정상 동작하는지 확인
// 조건: VU 1명, 5초간 실행
// ──────────────────────────────────────────────
import http from "k6/http";
import { check } from "k6";
import { Trend, Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL) {
  throw new Error("BASE_URL is not defined");
}

const latency = new Trend("response_latency", true);
const errorRate = new Rate("error_rate");

export const options = {
  scenarios: {
    smoke: {
      executor: "constant-vus",
      vus: 1,
      duration: "5s",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<3000"],
    error_rate: ["rate<0.5"],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/`, {
    tags: { name: "GET_root" },
    redirects: 0,
  });

  latency.add(res.timings.duration);

  const isSuccess = check(res, {
    "상태 코드 200 또는 302": (r) => r.status === 200 || r.status === 302,
    "응답 시간 1초 이내": (r) => r.timings.duration < 1000,
  });

  errorRate.add(!isSuccess);
}
