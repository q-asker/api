package com.icc.qasker.global.config;

import com.icc.qasker.global.properties.QAskerAsyncProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@Configuration
@RequiredArgsConstructor
public class AsyncConfig {

  private final QAskerAsyncProperties asyncProperties;

  /** 일반 비동기용 executor */
  @Bean(destroyMethod = "")
  public SimpleAsyncTaskExecutor taskExecutor() {
    return managedExecutor("async-", asyncProperties.getTaskTerminationTimeoutMs());
  }

  /** 퀴즈 생성용 */
  @Bean(destroyMethod = "")
  public SimpleAsyncTaskExecutor generationTaskExecutor() {
    return managedExecutor(
        "quiz-generation-", asyncProperties.getManagedTaskTerminationTimeoutMs());
  }

  /** 문항 재검토용 */
  @Bean(destroyMethod = "")
  public SimpleAsyncTaskExecutor qualityReviewTaskExecutor() {
    return managedExecutor("quality-review-", asyncProperties.getManagedTaskTerminationTimeoutMs());
  }

  private SimpleAsyncTaskExecutor managedExecutor(String threadNamePrefix, long terminationMs) {
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(threadNamePrefix);
    executor.setVirtualThreads(true);
    executor.setTaskTerminationTimeout(terminationMs);
    return executor;
  }
}
