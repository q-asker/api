package com.icc.qasker.ai.support;

import static java.util.Collections.unmodifiableList;

import java.util.ArrayList;
import java.util.List;

public final class ChunkPlanner {

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
