import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Counter } from "k6/metrics";

// 1. 테스트할 기본 URL
const BASE_URL = __ENV.BASE_URL || "https://api.q-asker.com";
// 2. 응답 시간 P95 임계값 (ms)
const CRITERION_P95 = parseInt(__ENV.CRITERION_P95) || 3500;
// 3. 테스트 최대 RPS
const MAX_RPS = parseInt(__ENV.MAX_RPS) || 50;

// 사용자 정의 지표(Metrics)
const problemSetGenerationRequestDuration = new Trend(
  "problem_set_generation_duration",
  true
);
const generationSuccess = new Counter("gen_success");
const generationFail = new Counter("gen_fail");

// 임계값(CRITERION_P95) 기준 성공/실패 카운터
const generationUnder = new Counter("gen_under");
const generationOver = new Counter("gen_over");

export const options = {
  scenarios: {
    // 'generation' 엔드포인트에 대한 램프 업(Ramp-up) 테스트
    load_test: {
      executor: "ramping-arrival-rate", // VUs가 아닌 RPS(도착률)를 점진적으로 증가
      timeUnit: "1s", // 1초당 'rate'만큼의 요청을 생성
      preAllocatedVUs: 50, // 시작 시 충분한 VU 할당
      maxVUs: 1000, // 최대 VU, (최대 RPS * 평균 응답 시간)보다 커야 함

      // 점진적으로 부하를 늘려 서버의 한계점을 찾습니다.
      stages: [
        // 1. 2분간 5 RPS까지 서서히 증가 (워밍업)
        { duration: "2m", target: 5 },
        // 2. 2분간 5 RPS 유지
        { duration: "2m", target: 5 },
        // 3. 5분간 5 -> 20 RPS까지 증가
        { duration: "5m", target: 20 },
        // 4. 3분간 20 RPS 유지
        { duration: "3m", target: 20 },
        // 5. 5분간 20 -> MAX_RPS (기본 50)까지 증가 (핵심 부하 구간)
        { duration: "5m", target: MAX_RPS },
        // 6. 3분간 MAX_RPS (기본 50) 유지
        { duration: "3m", target: MAX_RPS },
        // 7. 1분간 0 RPS로 감소 (쿨다운)
        { duration: "1m", target: 0 },
      ],
    },
  },

  thresholds: {
    // 95%의 요청이 CRITERION_P95 (2초) 안에 완료되어야 함
    problem_set_generation_duration: [`p(95)<${CRITERION_P95}`],
    // 전체 요청의 99% 이상이 성공해야 함 (실패율 1% 미만)
    http_req_failed: ["rate<0.01"],
    // 'gen_fail' 카운터가 0이어야 함 (200 OK가 아닌 모든 응답)
    gen_fail: ["count==0"],
  },
};

export default function () {
  // 1. mocking ai server에 대한 문제 생성 테스트
  const generationUrl = `${BASE_URL}/generation/mock`;
  const generationData = JSON.stringify({
    uploadedUrl: "uploadedUrl",
    quizCount: 5,
    quizType: "MULTIPLE",
    difficultyType: "STRATEGIC",
    pageNumbers: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
  });

  const generationRes = http.post(generationUrl, generationData, {
    headers: { "Content-Type": "application/json" },
    // k6가 태그를 사용해 'generation' 요청을 식별하도록 함
    tags: { name: "Generation" },
  });

  // 응답 시간 기록
  problemSetGenerationRequestDuration.add(generationRes.timings.duration);

  // 응답 상태 코드(200)에 따라 성공/실패 카운트
  const isSuccess = check(generationRes, {
    "generation: status is 200": (r) => r.status === 200,
  });

  if (isSuccess) {
    generationSuccess.add(1);
    // 200 OK 응답 중에서 임계값(CRITERION_P95) 기준으로 'Under'/'Over' 카운트
    if (generationRes.timings.duration < CRITERION_P95) {
      generationUnder.add(1);
    } else {
      generationOver.add(1);
    }
  } else {
    generationFail.add(1);
    // 실패 시 로그 출력
    console.error(
      `generation failed: status=${generationRes.status}, body=${generationRes.body}`
    );
  }
}
