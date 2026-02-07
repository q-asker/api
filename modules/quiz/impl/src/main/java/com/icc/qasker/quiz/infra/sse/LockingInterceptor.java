package com.icc.qasker.quiz.infra.sse;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

public class LockingInterceptor {

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * @RuntimeType: 리턴 타임이 달라도 유연하게 처리
     * @SuperCall: 원래 호출하려던 진짜 메서드 (super.send() 등)
     */
    @RuntimeType
    public Object intercept(@SuperCall Callable<?> superMethod) throws Exception {
        lock.lock();
        try {
            return superMethod.call();
        } finally {
            lock.unlock();
        }
    }
}