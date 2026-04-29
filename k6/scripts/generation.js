import http from 'k6/http';
import { check, sleep } from 'k6';

// 2. 환경 변수 설정
const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL) {
  throw new Error('BASE_URL is not defined');
}

export const options = {
  scenarios: {
    standard_load: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      stages: [
        { duration: '1m', target: 5 },
        { duration: '3m', target: 15 },
        { duration: '5m', target: 15 },
        { duration: '1m', target: 0 },
      ],
      preAllocatedVUs: 30,
      maxVUs: 80,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<30000'],
  },
};

const TEST_DATA = {
  uploadedUrl: "https://files.q-asker.com/9d1ea0c4-f9b3-465b-a9dc-6fd0ffb71704.pdf", 
  quizCount: 20,
  quizType: "MULTIPLE", 
  difficultyType: "RECALL",
  pageNumbers: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]
};

export default function () {
  const payload = JSON.stringify(TEST_DATA);
  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST_generation' }, 
  };

  // 1. 퀴즈 생성 요청 (/generation)
  const genRes = http.post(`${BASE_URL}/generation`, payload, params);
  
  const isGenSuccess = check(genRes, {
    'generated successfully': (r) => 
      r.status === 200 && r.json('problemSetId') !== undefined
  }, { type: 'generation' });

  if (!isGenSuccess) {
    return;
  }
  
  const problemSetId = genRes.json('problemSetId');

  // 2. 퀴즈 상세 조회 (/problem-set/{id})
  const getRes = http.get(`${BASE_URL}/problem-set/${problemSetId}`, {
    tags: { name: 'GET_problem_set' }
  });

  const isGetSuccess = check(getRes, {
    'retrieved successfully': (r) => 
      r.status === 200 && r.json('quiz') !== undefined
  }, { type: 'retrieval' });

  if (!isGetSuccess) {
    return;
  }

  sleep(1);
}