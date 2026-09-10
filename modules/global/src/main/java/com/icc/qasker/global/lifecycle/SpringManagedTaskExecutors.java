package com.icc.qasker.global.lifecycle;

import java.util.List;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class SpringManagedTaskExecutors implements SmartLifecycle {

  private final List<SimpleAsyncTaskExecutor> executors;

  public SpringManagedTaskExecutors(List<SimpleAsyncTaskExecutor> executors) {
    this.executors = executors;
  }

  @Override
  public void stop(Runnable callback) {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                closeAll();
              } finally {
                callback.run();
              }
            });
  }

  @Override
  public void stop() {
    closeAll();
  }

  private void closeAll() {
    // close를 호출하여 executor를 통해 새로운 작업이 수행되는것을 막는다
    List<Thread> closing =
        executors.stream().map(executor -> Thread.ofVirtual().start(executor::close)).toList();
    for (Thread thread : closing) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  @Override
  public void start() {}

  @Override
  public boolean isRunning() {
    return true;
  }
}
