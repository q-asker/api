package com.icc.qasker.global.query;

/** 요청당 Hibernate 쿼리수 카운터 — CountingInspector(증가)와 QueryCountInterceptor(읽기)가 공유. */
public class QueryCounter {

  private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);

  private QueryCounter() {}

  public static void increase() {
    COUNT.get()[0]++;
  }

  public static void reset() {
    COUNT.get()[0] = 0;
  }

  public static int get() {
    return COUNT.get()[0];
  }

  public static void clear() {
    COUNT.remove();
  }
}
