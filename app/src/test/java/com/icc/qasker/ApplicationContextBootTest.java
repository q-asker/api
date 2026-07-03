package com.icc.qasker;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * 버전업 세이프티 넷 (RT-001 + RT-004): Spring Boot / Spring AI patch·minor 승격과 resilience4j 승격이
 * ApplicationContext 부팅과 CircuitBreaker 등록 계약을 깨뜨리지 않음을 확인한다.
 *
 * <p>contextLoads()가 성공하면 전 모듈의 Bean 팩토리(ChatModel, Storage, DataSource 등)가 정상 구성된 것.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ApplicationContextBootTest {

  @Autowired private ApplicationContext applicationContext;

  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @Test
  void contextLoads() {
    assertThat(applicationContext).isNotNull();
    assertThat(applicationContext.getId()).isNotBlank();
  }

  @Test
  void chatModelBeanIsPresent() {
    assertThat(applicationContext.getBeanNamesForType(resolveChatModelType()))
        .as("Spring AI ChatModel bean 등록 확인")
        .isNotEmpty();
  }

  @Test
  void aiServerCircuitBreakerIsRegisteredWithExpectedConfig() {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("aiServer");
    assertThat(cb).isNotNull();
    assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(60.0f);
    assertThat(cb.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(3);
    assertThat(cb.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
    assertThat(cb.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState())
        .isEqualTo(2);
  }

  private static Class<?> resolveChatModelType() {
    try {
      return Class.forName("org.springframework.ai.chat.model.ChatModel");
    } catch (ClassNotFoundException e) {
      throw new AssertionError("Spring AI ChatModel 클래스가 런타임 클래스패스에 없음", e);
    }
  }
}
