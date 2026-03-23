package com.icc.qasker.ai;

public interface GeminiCacheService {

  /** 캐시 생성 후 캐시 이름과 토큰 수를 반환 */
  CacheInfo createCache(String fileUri, String strategyValue);

  void deleteCache(String cacheName);

  record CacheInfo(String name, long tokenCount) {}
}
