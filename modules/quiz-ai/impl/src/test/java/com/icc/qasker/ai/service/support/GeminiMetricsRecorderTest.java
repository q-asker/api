package com.icc.qasker.ai.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;

/**
 * 비용 계산 골든 회귀 테스트 (문서 검증 테스트 5). 제안 4·5 리팩터링 후에도 cached/thinking 토큰 분리 단가가 보존됨을 보장한다.
 *
 * <p>단가: input=0.5/1M, cache-read=0.05/1M, output=3.0/1M.
 */
class GeminiMetricsRecorderTest {

  private GeminiMetricsRecorder recorder() {
    return new GeminiMetricsRecorder(new SimpleMeterRegistry(), 0.5, 0.05, 3.0);
  }

  @Test
  void recordChunkResult_plainUsage_appliesInputAndOutputPricing() {
    Usage usage = mock(Usage.class);
    when(usage.getPromptTokens()).thenReturn(1_000_000);
    when(usage.getCompletionTokens()).thenReturn(1_000_000);

    double cost = recorder().recordChunkResult(100L, usage);

    // input 1M * 0.5 + output 1M * 3.0 = 3.5
    assertThat(cost).isEqualTo(3.5);
  }

  @Test
  void recordChunkResult_googleUsage_separatesCachedAndThinkingPricing() {
    GoogleGenAiUsage usage = mock(GoogleGenAiUsage.class);
    when(usage.getPromptTokens()).thenReturn(2_000_000);
    when(usage.getCompletionTokens()).thenReturn(1_000_000);
    when(usage.getCachedContentTokenCount()).thenReturn(1_000_000);
    when(usage.getThoughtsTokenCount()).thenReturn(500_000);

    double cost = recorder().recordChunkResult(100L, usage);

    // nonCached input 1M*0.5=0.5, cache 1M*0.05=0.05, output 1M*3.0=3.0, thinking 0.5M*3.0=1.5
    assertThat(cost).isEqualTo(5.05);
  }
}
