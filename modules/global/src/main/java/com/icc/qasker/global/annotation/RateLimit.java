package com.icc.qasker.global.annotation;

import com.icc.qasker.global.ratelimit.RateLimitTier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Controller 메서드에 Rate Limit 등급을 지정하는 어노테이션. 미지정 시 기본 등급 READ가 적용된다. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

  /** 적용할 Rate Limit 등급 */
  RateLimitTier value();

  /** true이면 클라이언트 단위가 아닌 서버 전체에서 버킷을 공유한다. 모든 사용자가 동일한 토큰 풀을 소비하므로 서버 전체 처리량을 제한할 때 사용한다. */
  boolean global() default false;
}
