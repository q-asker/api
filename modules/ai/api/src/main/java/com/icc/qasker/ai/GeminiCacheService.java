package com.icc.qasker.ai;

public interface GeminiCacheService {

  String createCache(String fileUri, String strategyValue);

  void deleteCache(String cacheName);
}
