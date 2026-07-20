package com.icc.qasker.ai.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChunkPlannerTest {

  @Test
  @DisplayName("chunkSize 단위로 분할하고 마지막 청크는 잔여분만큼")
  void plan_splitsByChunkSizeWithRemainder() {
    assertThat(ChunkPlanner.plan(25, 15)).containsExactly(15, 10);
    assertThat(ChunkPlanner.plan(30, 15)).containsExactly(15, 15);
    assertThat(ChunkPlanner.plan(10, 15)).containsExactly(10);
  }

  @Test
  @DisplayName("requestedCount가 0 이하이면 빈 목록")
  void plan_nonPositiveCount_returnsEmpty() {
    assertThat(ChunkPlanner.plan(0, 15)).isEmpty();
    assertThat(ChunkPlanner.plan(-1, 15)).isEmpty();
  }

  @Test
  @DisplayName("chunkSize가 1 미만이면 1로 보정해 무한 루프를 방지")
  void plan_nonPositiveChunkSize_clampsToOne() {
    assertThat(ChunkPlanner.plan(3, 0)).containsExactly(1, 1, 1);
    assertThat(ChunkPlanner.plan(2, -5)).containsExactly(1, 1);
  }

  @Test
  @DisplayName("반환 목록은 불변")
  void plan_returnsImmutableList() {
    List<Integer> plan = ChunkPlanner.plan(5, 15);
    org.junit.jupiter.api.Assertions.assertThrows(
        UnsupportedOperationException.class, () -> plan.add(1));
  }
}
