import http from 'k6/http';
import { check, sleep } from 'k6';

// 1. 29MB 더미 데이터 생성
// 29MB = 29 * 1024 * 1024 bytes
const FILE_SIZE = 29 * 1024 * 1024;
const pdfFile = new Uint8Array(FILE_SIZE);
const pdfHeader = [0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34];
pdfFile.set(pdfHeader, 0);

export const options = {
  vus: 30,
  duration: '30s',
};

const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL) {
  throw new Error('BASE_URL is not defined');
}

export default function () {
  // --- Step 1: 서버에 Presigned URL 요청 ---
  
  const uniqueFileName = `test-doc-${Date.now()}-${__VU}-${__ITER}.pdf`;
  
  // DTO (PresignRequest) 구조에 맞춰 수정
  const presignPayload = JSON.stringify({
    originalFileName: uniqueFileName, // DTO 필드명: originalFileName
    contentType: 'application/pdf',   // DTO 필수값
    fileSize: FILE_SIZE               // DTO 필수값
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const presignRes = http.post(`${BASE_URL}/s3/request-presign`, presignPayload, params);

  const presignSuccess = check(presignRes, {
    'Presigned URL 발급 성공': (r) => r.status === 200,
  });

  if (!presignSuccess) {
    console.error(`URL 발급 실패: ${presignRes.status} ${presignRes.body}`);
    return;
  }

  // 응답 DTO (PresignResponse) 구조 매핑
  const presignedUrl = presignRes.json('uploadUrl'); 

  // --- Step 2: S3로 직접 바이너리 업로드 ---
  
  const uploadRes = http.put(presignedUrl, pdfFile.buffer, {
    headers: {
      'Content-Type': 'application/pdf', 
    },
  });

  check(uploadRes, {
    'S3 업로드 성공': (r) => r.status === 200,
  });

  sleep(1);
}