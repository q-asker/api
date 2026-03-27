package com.icc.qasker.global.ratelimit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Rate Limit 등급 정의.
 *
 * <p>각 등급은 기본 capacity(버킷 최대 토큰 수)와 refillPerMinute(분당 토큰 보충 수)를 가진다. YAML 설정으로 오버라이드 가능.
 */
@Getter
@RequiredArgsConstructor
public enum RateLimitTier {
  // AI + S3 + 파일변환 등 고비용 API
  CRITICAL(5, 5),
  // SSE 연결, 해설 조회 등 서버 리소스 점유 API
  HEAVY(10, 10),
  // 게시글/댓글 쓰기 등 변경 API
  WRITE(20, 20),
  // 인증 관련 API
  STANDARD(10, 10),
  // 일반 조회 API
  READ(60, 60),
  // 헬스체크 등 제한 없음
  NONE(0, 0);

  private final long defaultCapacity;
  private final long defaultRefillPerMinute;
}
