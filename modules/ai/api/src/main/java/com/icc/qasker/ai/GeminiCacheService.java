package com.icc.qasker.ai;

public interface GeminiCacheService {

    String createCache(String fileUri, String strategyValue, String jsonSchema);

    void deleteCache(String cacheName);
}
