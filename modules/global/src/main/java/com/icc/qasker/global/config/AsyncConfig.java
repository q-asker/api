package com.icc.qasker.global.config;

import com.icc.qasker.global.properties.QAskerAsyncProperties;
import java.util.concurrent.Executor;
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

  @Bean
  public Executor taskExecutor() {
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
    executor.setVirtualThreads(true);
    executor.setTaskTerminationTimeout(asyncProperties.getTaskTerminationTimeoutMs());
    return executor;
  }
}
