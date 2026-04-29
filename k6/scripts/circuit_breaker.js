import http from 'k6/http';
import { check, sleep } from 'k6';

// 테스트 설정 (Configuration)
export const options = {
  scenarios: {
    // 시나리오 1: 장애가 발생하는 외부 연동 API 집중 공략
    risky_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },  // 30초 동안 VU 50명까지 증가
        { duration: '1m', target: 50 },   // 1분 유지
        { duration: '30s', target: 0 },   // 30초 동안 종료
      ],
      exec: 'riskyApi', // 실행할 함수 지정
    },
    // 시나리오 2: 장애 전파 여부를 확인하기 위한 일반 API 호출
    safe_load: {
      executor: 'constant-vus',
      vus: 10,  // 항상 10명의 유저가 대기
      duration: '2m',
      exec: 'safeApi', // 실행할 함수 지정
    },
  },
  // 목표 임계치 (Thresholds) - 이력서 수치의 근거가 됨
  thresholds: {
    // Risky API는 서킷이 열리면 Fallback 덕분에 5초가 아닌 1초 이내여야 함
    'http_req_duration{scenario:risky_load}': ['p(95)<1500'], 
    // Safe API는 외부 장애와 상관없이 항상 빨라야 함 (격리 성공 증명)
    'http_req_duration{scenario:safe_load}': ['p(99)<200'], 
    // 에러율은 1% 미만이어야 함 (Fallback 리턴으로 인해 200 OK 처리)
    'http_req_failed': ['rate<0.01'], 
  },
};

const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL) {
  throw new Error("BASE_URL is not defined");
}

// 1. 장애 유발 API 호출 함수
export function riskyApi() {
  const res = http.get(`${BASE_URL}/api/risky`);
  
  check(res, {
    'Risky status is 200 (Fallback)': (r) => r.status === 200,
    // 서킷 브레이커가 동작하면 응답이 빨라야 함 (예: 1초 타임아웃)
    'Risky Response is fast (Circuit Open)': (r) => r.timings.duration < 1500,
  });
  sleep(1);
}

// 2. 일반 API 호출 함수 (영향도 체크)
export function safeApi() {
  const res = http.get(`${BASE_URL}/api/safe`);
  
  check(res, {
    'Safe status is 200': (r) => r.status === 200,
    // 스레드 풀이 고갈되지 않았다면 즉시 응답해야 함
    'Safe Response is instant': (r) => r.timings.duration < 100, 
  });
  sleep(1);
}