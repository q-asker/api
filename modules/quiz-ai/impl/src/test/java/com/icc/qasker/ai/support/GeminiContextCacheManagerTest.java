package com.icc.qasker.ai.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeminiContextCacheManagerTest {

  @Test
  void detectsMinimumTokenRejectionInCauseChain() {
    // Spring AI 는 Gemini 400 을 "Failed to create cached content" 예외로 감싸 던진다
    Exception gemini =
        new IllegalStateException(
            "400 . The cached content is of 1656 tokens. The minimum token count to start"
                + " explicit caching is 4096.");
    Exception wrapped = new RuntimeException("Failed to create cached content", gemini);

    assertThat(GeminiContextCacheManager.isBelowMinimumTokens(wrapped)).isTrue();
  }

  @Test
  void otherFailuresAreNotTreatedAsShortContent() {
    Exception billing =
        new RuntimeException(
            "Failed to create cached content",
            new IllegalStateException("403 . This API method requires billing to be enabled."));

    assertThat(GeminiContextCacheManager.isBelowMinimumTokens(billing)).isFalse();
  }
}
