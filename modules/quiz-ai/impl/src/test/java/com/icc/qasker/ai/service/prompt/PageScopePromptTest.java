package com.icc.qasker.ai.service.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageScopePromptTest {

  @Test
  void groupsConsecutivePagesIntoRanges() {
    assertThat(PageScopePrompt.toRanges(List.of(1, 2, 3, 5, 7, 8))).isEqualTo("1-3, 5, 7-8");
  }

  @Test
  void sortsAndDeduplicatesBeforeGrouping() {
    assertThat(PageScopePrompt.toRanges(List.of(8, 3, 1, 2, 2, 7))).isEqualTo("1-3, 7-8");
  }

  @Test
  void singlePage() {
    assertThat(PageScopePrompt.toRanges(List.of(4))).isEqualTo("4");
  }

  @Test
  void promptCarriesRangesOnly() {
    // 오답 선지는 범위 밖 내용도 쓸 수 있게 묶지 않는다 — 지시는 출제 범위 한 줄뿐이다
    assertThat(PageScopePrompt.of(List.of(4, 5, 6)))
        .startsWith("[출제 범위]")
        .contains("4-6페이지")
        .doesNotContain("오답");
  }

  @Test
  void emptyWhenNoPages() {
    assertThat(PageScopePrompt.of(null)).isEmpty();
    assertThat(PageScopePrompt.of(List.of())).isEmpty();
  }
}
