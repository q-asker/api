import http from 'k6/http';
import { check, sleep } from 'k6';

// 29MB 더미 데이터 생성
const pdfFile = new Uint8Array(29 * 1024 * 1024); 

const pdfHeader = [0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34]; 
pdfFile.set(pdfHeader, 0); 

const binFile = http.file(pdfFile.buffer, 'test-doc.pdf', 'application/pdf');

export const options = {
  vus: 30,
  duration: '30s',
};

const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL) {
  throw new Error('BASE_URL is not defined');
}

export default function () {
  const payload = {
    file: binFile, 
  };

  const res = http.post(`${BASE_URL}/s3/upload`, payload);

  check(res, {
    '서버 업로드 성공': (r) => r.status === 200,
  });

  sleep(1);
}
