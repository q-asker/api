// files.q-asker.com — 업로드 PDF 를 GCS(q-asker-ai-files-v2)에서 읽어 내준다.
// GCS 가 PDF 의 단일 저장소다: 백엔드가 업로드 시 <uuid>.pdf 로 저장하고, 생성 시 같은 객체를 gs:// 로 Gemini 에 넘긴다.
// 보관 기한(버킷 수명 주기 7일)이 지난 객체는 GCS 가 404 를 돌려주고, 그대로 전달한다.
const GCS_BASE = 'https://storage.googleapis.com/q-asker-ai-files-v2';

export default {
  async fetch(request) {
    const url = new URL(request.url);
    const pathname = url.pathname;

    if (pathname === '/' || pathname === '') {
      return new Response('Not Found', { status: 404 });
    }

    const originHeaders = new Headers(request.headers);
    originHeaders.delete('host');
    originHeaders.delete('if-range');

    const response = await fetch(`${GCS_BASE}${pathname}`, {
      method: request.method,
      headers: originHeaders,
    });

    const headers = new Headers(response.headers);
    headers.set('Cache-Control', 'public, max-age=86400');
    headers.set('Access-Control-Allow-Origin', '*');
    headers.delete('Access-Control-Allow-Credentials');

    return new Response(response.body, {
      status: response.status,
      headers,
    });
  },
};
