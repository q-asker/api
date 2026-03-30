package com.icc.qasker.global.ratelimit;

/**
 * Rate Limit 등급 정의.
 *
 * <p>각 등급의 capacity와 refillPerMinute는 application.yml의 q-asker.rate-limit.tiers 섹션에서 관리한다.
 */
public enum RateLimitTier {
  // AI + S3 + 파일변환 등 고비용 API
  CRITICAL,
  // SSE 연결, 해설 조회 등 서버 리소스 점유 API
  HEAVY,
  // 게시글/댓글 쓰기 등 변경 API
  WRITE,
  // 인증 관련 API
  STANDARD,
  // 일반 조회 API
  READ,
  // 헬스체크 등 제한 없음
  NONE;
}
