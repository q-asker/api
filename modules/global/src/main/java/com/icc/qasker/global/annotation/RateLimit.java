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
}
