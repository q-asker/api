package com.icc.qasker.global.query;

import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * @Scheduled 실행을 MDC 스코프로 감싼다 — HTTP의 MdcRequestFilter와 같은 역할을 스케줄러 스레드에 해준다.
 */
@Aspect
@Component
@Profile("loadtest")
public class ScheduledTraceAspect {

  @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
  public Object trace(ProceedingJoinPoint pjp) throws Throwable {
    try {
      MDC.put("reqId", UUID.randomUUID().toString().substring(0, 8));
      MDC.put(
          "uri",
          "SCHED:"
              + pjp.getSignature().getDeclaringType().getSimpleName()
              + "."
              + pjp.getSignature().getName());
      return pjp.proceed();
    } finally {
      MDC.clear();
    }
  }
}
