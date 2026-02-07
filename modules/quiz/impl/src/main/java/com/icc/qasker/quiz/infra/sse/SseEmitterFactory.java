package com.icc.qasker.quiz.infra.sse;

import com.icc.qasker.global.error.ExceptionMessage;
import java.io.IOException;
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
            log.error("SseEmitter 프록시 생성 실패", e);

            SseEmitter fallbackEmitter = new SseEmitter(10 * 1000L);
            try {
                fallbackEmitter.send(SseEmitter.event()
                    .name("error")
                    .data(ExceptionMessage.DEFAULT_ERROR.getMessage()));
                return fallbackEmitter;
            } catch (IOException ioException) {
                log.error("fallback 에러 메시지 전송 실패", ioException);
                return null;
            }
        }
    }
}