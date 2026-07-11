package com.icc.qasker.ai.service.support;

import static java.util.Collections.unmodifiableList;

import java.util.ArrayList;
import java.util.List;

/** 요청 문항 수를 chunk-size 단위 배치로 분할하는 순수 함수. 마지막 배치는 잔여분만큼. */
public final class ChunkPlanner {

  private ChunkPlanner() {}

  /**
   * 요청 문항 수를 chunkSize 단위 청크로 분할한다.
   *
   * <p>예: requestedCount=25, chunkSize=15 → [15, 10]
   *
   * @param requestedCount 요청 문항 수. 0 이하이면 빈 목록.
   * @param chunkSize 청크 크기. 1 미만이면 1로 보정해 무한 루프를 방지한다.
   */
  public static List<Integer> plan(int requestedCount, int chunkSize) {
    if (requestedCount <= 0) return List.of();
    int size = Math.max(1, chunkSize);
    ArrayList<Integer> plan = new ArrayList<>();
    int remaining = requestedCount;
    while (remaining > 0) {
      int next = Math.min(size, remaining);
      plan.add(next);
      remaining -= next;
    }
    return unmodifiableList(plan);
  }
}
