package com.icc.qasker.quiz.infra.sse;

import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class SseEmitterFactory {

    public static SseEmitter createThreadSafeEmitter(Long timeout) {
        try {
            return new ByteBuddy()
                .subclass(SseEmitter.class)
                .method(ElementMatchers.nameStartsWith("send"))
                .intercept(MethodDelegation.to(new LockingInterceptor()))
                .make()
                .load(SseEmitterFactory.class.getClassLoader())
                .getLoaded()
                .getConstructor(Long.class)
                .newInstance(timeout);
        } catch (Exception e) {
            log.error("SseEmitter 프록시 생성 실패 원인: {}", e.getMessage());
            throw new RuntimeException("SseEmitter 프록시 생성 실패", e);
        }
    }
}