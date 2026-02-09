package com.icc.qasker.quiz.infra;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.NonNull;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseEmitterFactory {

    public static SseEmitter createThreadSafeEmitter(Long timeout) {
        return new ThreadSafeSseEmitter(timeout);
    }


    public static class ThreadSafeSseEmitter extends SseEmitter {

        private final ReentrantLock lock = new ReentrantLock();

        public ThreadSafeSseEmitter(Long timeout) {
            super(timeout);
        }

        @Override
        public void send(@NonNull Object object) throws IOException {
            lock.lock();
            try {
                super.send(object);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void send(@NonNull SseEventBuilder builder) throws IOException {
            lock.lock();
            try {
                super.send(builder);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void complete() {
            lock.lock();
            try {
                super.complete();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public synchronized void completeWithError(@NonNull Throwable ex) {
            lock.lock();
            try {
                super.completeWithError(ex);
            } finally {
                lock.unlock();
            }
        }
    }
}